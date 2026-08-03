package com.scribble.backend.service;

import java.security.SecureRandom;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import com.scribble.backend.dto.ChatOrGuessBroadcast;
import com.scribble.backend.dto.CurrentWordMessage;
import com.scribble.backend.dto.GameStateMessage;
import com.scribble.backend.dto.PlayerListMessage;
import com.scribble.backend.dto.WordChoicesMessage;

import com.scribble.backend.model.GameRoom;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GameService {
    private static final int ROUND_DURATION_SECONDS = 60;
    private static final int CHOOSE_WORD_DURATION_SECONDS = 15;
    private static final int HINT_INTERVAL_SECONDS = 15; // reveal a letter this often

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Map<String, ScheduledFuture<?>> activeTimers = new ConcurrentHashMap<>();
    private final RoomService roomService;
    private final WordBank wordBank;
    private final SimpMessagingTemplate messagingTemplate;
    private final SecureRandom random = new SecureRandom();

    public GameService(RoomService roomService, WordBank wordBank, SimpMessagingTemplate messagingTemplate) {
        this.roomService = roomService;
        this.wordBank = wordBank;
        this.messagingTemplate = messagingTemplate;
    }

    public void startGame(String roomCode, String playerId) {
        GameRoom room = roomService.getRoom(roomCode);
        if (room == null) return;

        room.withLock(() -> {
            if (!room.isHost(playerId)) return; // only the host can start/restart

            room.getTurnOrder().clear();
            room.getTurnOrder().addAll(room.getPlayers().keySet());
            room.setCurrentTurnIndex(0);
            room.setCurrentRound(0);
            startTurn(room);
        });
    }

    public void updateSettings(String roomCode, String playerId, int totalRounds, boolean infiniteRounds) {
        GameRoom room = roomService.getRoom(roomCode);
        if (room == null) return;

        room.withLock(() -> {
            if (!room.isHost(playerId)) return;
            // don't allow changing settings mid-round; only when idle or after a game ends
            if (room.getState() == GameRoom.GameState.DRAWING
                    || room.getState() == GameRoom.GameState.CHOOSING_WORD
                    || room.getState() == GameRoom.GameState.ROUND_END) return;

            int clamped = Math.max(1, Math.min(totalRounds, 20));
            room.setTotalRounds(clamped);
            room.setInfiniteRounds(infiniteRounds);

            broadcastState(room); // pushes the new settings to everyone in the room
        });
    }

    private void startTurn(GameRoom room) {
        cancelTimer(room.getRoomCode());
        room.clearStrokes();
        room.resetHints();

        List<String> order = room.getTurnOrder();
        if (order.isEmpty()) return;

        String drawerId = order.get(room.getCurrentTurnIndex());
        room.setCurrentDrawerId(drawerId);
        room.setState(GameRoom.GameState.CHOOSING_WORD);
        room.setTimeRemainingSeconds(CHOOSE_WORD_DURATION_SECONDS);

        List<String> options = wordBank.getRandomOptions(3);
        room.setWordOptions(options);

        messagingTemplate.convertAndSendToUser(
                drawerId,
                "/queue/word-choices",
                new WordChoicesMessage(options)
        );

        broadcastState(room);
        broadcastPlayers(room);
        startChoosingTimer(room);
    }

    private void startChoosingTimer(GameRoom room) {
        cancelTimer(room.getRoomCode());

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            room.withLock(() -> {
                if (room.getState() != GameRoom.GameState.CHOOSING_WORD) {
                    cancelTimer(room.getRoomCode());
                    return;
                }

                int remaining = room.getTimeRemainingSeconds() - 1;
                room.setTimeRemainingSeconds(remaining);

                if (remaining <= 0) {
                    cancelTimer(room.getRoomCode());
                    autoChooseWord(room);
                    return;
                }
                broadcastState(room);
            });
        }, 1, 1, TimeUnit.SECONDS);

        activeTimers.put(room.getRoomCode(), future);
    }

    // call only from inside withLock
    private void autoChooseWord(GameRoom room) {
        List<String> options = room.getWordOptionsSnapshot();
        if (options.isEmpty()) return;
        String word = options.get(random.nextInt(options.size()));
        applyChosenWord(room, word);
    }

    public void chooseWord(String roomCode, String playerId, String chosenWord) {
        GameRoom room = roomService.getRoom(roomCode);
        if (room == null) return;

        room.withLock(() -> {
            if (playerId == null || !playerId.equals(room.getCurrentDrawerId())) return;
            if (room.getState() != GameRoom.GameState.CHOOSING_WORD) return;

            applyChosenWord(room, chosenWord);
        });
    }

    // call only from inside withLock
    private void applyChosenWord(GameRoom room, String chosenWord) {
        room.setCurrentWord(chosenWord);
        room.resetHints();
        room.setState(GameRoom.GameState.DRAWING);
        room.setTimeRemainingSeconds(ROUND_DURATION_SECONDS);

        broadcastState(room);
        sendCurrentWordToDrawer(room);
        startTimer(room);
    }

    /** Privately tells the current drawer the actual word — never broadcast to guessers. */
    private void sendCurrentWordToDrawer(GameRoom room) {
        if (room.getCurrentDrawerId() == null || room.getCurrentWord() == null) return;
        messagingTemplate.convertAndSendToUser(
                room.getCurrentDrawerId(),
                "/queue/current-word",
                new CurrentWordMessage(room.getCurrentWord())
        );
    }

    private void startTimer(GameRoom room) {
        cancelTimer(room.getRoomCode());

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            room.withLock(() -> {
                int remaining = room.getTimeRemainingSeconds() - 1;
                room.setTimeRemainingSeconds(remaining);

                if (remaining <= 0) {
                    cancelTimer(room.getRoomCode());
                    room.setState(GameRoom.GameState.ROUND_END);
                    broadcastState(room);
                    scheduler.schedule(() -> room.withLock(() -> advanceTurn(room)), 3, TimeUnit.SECONDS);
                    return;
                }

                maybeRevealHint(room, remaining);
                broadcastState(room);
            });
        }, 1, 1, TimeUnit.SECONDS);

        activeTimers.put(room.getRoomCode(), future);
    }

    /** Reveals one more letter every HINT_INTERVAL_SECONDS of elapsed time, capping at ~half the word. */
    private void maybeRevealHint(GameRoom room, int remaining) {
        String word = room.getCurrentWord();
        if (word == null) return;

        int elapsed = ROUND_DURATION_SECONDS - remaining;
        if (elapsed <= 0 || elapsed % HINT_INTERVAL_SECONDS != 0) return;

        long letterCount = word.chars().filter(Character::isLetter).count();
        int maxHints = (int) Math.max(1, (letterCount - 1) / 2); // never reveal the whole word

        room.revealRandomHint(random, maxHints);
    }

    private void cancelTimer(String roomCode) {
        ScheduledFuture<?> existing = activeTimers.remove(roomCode);
        if (existing != null) existing.cancel(false);
    }

    private void broadcastState(GameRoom room) {
        // reveal the actual word only during the brief ROUND_END window
        String revealed = (room.getState() == GameRoom.GameState.ROUND_END)
                ? room.getCurrentWord()
                : null;

        messagingTemplate.convertAndSend(
                "/topic/room/" + room.getRoomCode() + "/state",
                new GameStateMessage(
                        room.getState().name(),
                        room.getMaskedWord(),
                        room.getCurrentDrawerId(),
                        room.getTimeRemainingSeconds(),
                        room.getCurrentRound(),
                        room.getTotalRounds(),
                        room.isInfiniteRounds(),
                        revealed
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

                messagingTemplate.convertAndSend(
                        "/topic/room/" + roomCode + "/chat",
                        new ChatOrGuessBroadcast(playerName, "guessed the word!", true)
                );
                broadcastPlayers(room);

                int totalGuessers = room.getPlayers().size() - 1;
                if (room.getCorrectGuessers().size() >= totalGuessers && totalGuessers > 0) {
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
        room.clearStrokes();
        room.resetHints();

        int nextIndex = room.getCurrentTurnIndex() + 1;

        if(nextIndex >= room.getTurnOrder().size()){
            room.setCurrentRound(room.getCurrentRound()+1);
            nextIndex = 0;
        }

        if(!room.isInfiniteRounds() && room.getCurrentRound() >= room.getTotalRounds()){
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
                new PlayerListMessage(room.toPlayerDtos(), room.getHostPlayerId())
        );
    }
}