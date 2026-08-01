package com.scribble.backend.service;
import java.sql.Time;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import com.scribble.backend.dto.ChatOrGuessBroadcast;
import com.scribble.backend.dto.GameStateMessage;
import com.scribble.backend.dto.PlayerListMessage;
import com.scribble.backend.dto.WordChoicesMessage;

import com.scribble.backend.model.GameRoom;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GameService {
    private static final int TOTAL_ROUNDS = 3;
    private static final int ROUND_DURATION_SECONDS = 60;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Map<String, ScheduledFuture<?>> activeTimers = new ConcurrentHashMap<>();
    private final RoomService roomService;
    private final WordBank wordBank;
    private final SimpMessagingTemplate messagingTemplate;

    public GameService(RoomService roomService, WordBank wordBank, SimpMessagingTemplate messagingTemplate) {
        this.roomService = roomService;
        this.wordBank = wordBank;
        this.messagingTemplate = messagingTemplate;
    }

    public void startGame(String roomCode) {
        GameRoom room = roomService.getRoom(roomCode);
        System.out.println("START GAME CALLED: " + roomCode);
        if (room == null) {
            System.out.println("START GAME: room not found for " + roomCode);
            return;
        }

        room.withLock(() -> {

            room.getTurnOrder().clear();
            room.getTurnOrder().addAll(room.getPlayers().keySet());
            room.setCurrentTurnIndex(0);
            System.out.println("TURN ORDER: " + room.getTurnOrder());
            startTurn(room);
        });
    }

    private void startTurn(GameRoom room) {
        cancelTimer(room.getRoomCode());

        List<String> order = room.getTurnOrder();
        if (order.isEmpty()) {
            System.out.println("START TURN: turn order is empty, aborting");
            return;
        }

        String drawerId = order.get(room.getCurrentTurnIndex());
        room.setCurrentDrawerId(drawerId);
        room.setState(GameRoom.GameState.CHOOSING_WORD);

        List<String> options = wordBank.getRandomOptions(3);
        System.out.println("STARTING TURN: drawer=" + drawerId + " options=" + options);

        messagingTemplate.convertAndSendToUser(
                drawerId,
                "/queue/word-choices",
                new WordChoicesMessage(options)
        );

        broadcastState(room);
        broadcastPlayers(room);
    }
    public void chooseWord(String roomCode, String chosenWord) {
        GameRoom room = roomService.getRoom(roomCode);
        if (room == null) return;

        room.withLock(() -> {
            room.setCurrentWord(chosenWord);
            room.setState(GameRoom.GameState.DRAWING);
            room.setTimeRemainingSeconds(ROUND_DURATION_SECONDS);

            System.out.println("WORD CHOSEN: " + chosenWord + " for room " + roomCode);
            broadcastState(room);
            startTimer(room);
        });
    }
    private void startTimer(GameRoom room) {
        cancelTimer(room.getRoomCode());

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            room.withLock(() -> {
                int remaining = room.getTimeRemainingSeconds() - 1;
                room.setTimeRemainingSeconds(remaining);

                if (remaining <= 0) {
                    System.out.println("ROUND ENDED (time up) for room " + room.getRoomCode());
                    cancelTimer(room.getRoomCode());
                    room.setState(GameRoom.GameState.ROUND_END);
                    broadcastState(room);
                    scheduler.schedule(() -> room.withLock(() -> advanceTurn(room)), 3, TimeUnit.SECONDS);
                    return; // exits the inner withLock lambda, skips the line below
                }
                broadcastState(room);
            });
        }, 1, 1, TimeUnit.SECONDS);

        activeTimers.put(room.getRoomCode(), future);
    }




    private void cancelTimer(String roomCode) {
        ScheduledFuture<?> existing = activeTimers.remove(roomCode);
        if (existing != null) existing.cancel(false);
    }

    private void broadcastState(GameRoom room) {
        messagingTemplate.convertAndSend(
                "/topic/room/" + room.getRoomCode() + "/state",
                new GameStateMessage(
                        room.getState().name(),
                        room.getMaskedWord(),
                        room.getCurrentDrawerId(),
                        room.getTimeRemainingSeconds()
                )
        );
    }

    public void submitGuess(String roomCode, String playerId, String guessText) {
        GameRoom room = roomService.getRoom(roomCode);
        if (room == null) return;

        room.withLock(() -> {
            if (room.getState() != GameRoom.GameState.DRAWING) return;
            if (playerId.equals(room.getCurrentDrawerId())) return;
            if (room.getCorrectGuessers().contains(playerId)) return;

            String playerName = room.getPlayers().get(playerId);
            boolean correct = guessText.trim().equalsIgnoreCase(room.getCurrentWord());

            if (correct) {
                room.getCorrectGuessers().add(playerId);

                int points = (int) (100.0 * room.getTimeRemainingSeconds() / ROUND_DURATION_SECONDS);
                room.getScores().merge(playerId, points, Integer::sum);
                room.getScores().merge(room.getCurrentDrawerId(), 10, Integer::sum);

                System.out.println("CORRECT GUESS: " + playerName + " earned " + points + " points");

                messagingTemplate.convertAndSend(
                        "/topic/room/" + roomCode + "/chat",
                        new ChatOrGuessBroadcast(playerName, "guessed the word!", true)
                );
                broadcastPlayers(room);

                int totalGuessers = room.getPlayers().size() - 1;
                if (room.getCorrectGuessers().size() >= totalGuessers && totalGuessers > 0) {
                    System.out.println("EVERYONE GUESSED - ending round early");
                    endRoundEarly(room);
                }
            } else {
                messagingTemplate.convertAndSend(
                        "/topic/room/" + roomCode + "/chat",
                        new ChatOrGuessBroadcast(playerName, guessText, false)
                );
            }
        });
    }

    private void endRoundEarly(GameRoom room) {
        cancelTimer(room.getRoomCode());
        room.setState(GameRoom.GameState.ROUND_END);
        room.resetCorrectGuessers();
        broadcastState(room);
        scheduler.schedule(() -> room.withLock(() -> advanceTurn(room)), 3, TimeUnit.SECONDS);
    }

    private void advanceTurn(GameRoom room){
        room.resetCorrectGuessers();
        room.setCurrentWord(null);

        int nextIndex = room.getCurrentTurnIndex() + 1;

        if(nextIndex >= room.getTurnOrder().size()){
            room.setCurrentRound(room.getCurrentRound()+1);
            nextIndex =0;
        }

        if(room.getCurrentRound() >= TOTAL_ROUNDS){
            room.setState(GameRoom.GameState.GAME_OVER);
            broadcastState(room);
            broadcastPlayers(room);
            return;
        }
        room.setCurrentTurnIndex(nextIndex);
        startTurn(room);
    }

    private void broadcastPlayers(GameRoom room){
        messagingTemplate.convertAndSend(
                "/topic/room/" + room.getRoomCode() + "/players",
                new PlayerListMessage(room.toPlayerDtos())
        );
    }

}