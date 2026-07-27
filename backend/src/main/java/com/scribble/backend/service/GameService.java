package com.scribble.backend.service;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import com.scribble.backend.dto.GameStateMessage;
import com.scribble.backend.dto.WordChoicesMessage;

import com.scribble.backend.model.GameRoom;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GameService {

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

        messagingTemplate.convertAndSend(
                "/topic/room/" + room.getRoomCode() + "/word-choices",
                new WordChoicesMessage(options)
        );

        broadcastState(room);
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
}