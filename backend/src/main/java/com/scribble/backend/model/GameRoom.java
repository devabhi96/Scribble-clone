package com.scribble.backend.model;

import com.scribble.backend.dto.DrawBatchMessage;
import com.scribble.backend.dto.PlayerDto;
import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;

@Getter
public class GameRoom {
    @Setter private int currentRound = 0;
    @Setter private int timeRemainingSeconds = 0;
    private final Set<String> correctGuessers = new HashSet<>();
    private final Map<String, Integer> scores = new ConcurrentHashMap<>();

    public enum GameState {
        WAITING, CHOOSING_WORD, DRAWING, ROUND_END, GAME_OVER
    }

    private final String roomCode;
    private final Map<String,String> players = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    @Setter private GameState state = GameState.WAITING;
    @Setter private String currentDrawerId;
    @Setter private String currentWord;
    @Setter private int currentTurnIndex = 0;

    private final List<String> turnOrder = new ArrayList<>();


    private final List<DrawBatchMessage> strokeHistory = new ArrayList<>();
    private final Set<String> disconnectedPlayers = new HashSet<>();
    private final Map<String, ScheduledFuture<?>> pendingRemovals = new ConcurrentHashMap<>();

    public GameRoom(String roomCode){
        this.roomCode = roomCode;
    }

    public void withLock(Runnable action){
        lock.lock();
        try{
            action.run();
        }
        finally{
            lock.unlock();
        }
    }

    public String getMaskedWord(){
        if(currentWord == null) return "";
        return currentWord.replaceAll("[a-zA-Z]","_ ").trim();
    }

    public void resetCorrectGuessers() {
        correctGuessers.clear();
    }

    public List<PlayerDto> toPlayerDtos() {
        List<PlayerDto> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : players.entrySet()) {
            String id = entry.getKey();
            String name = entry.getValue();
            int score = scores.getOrDefault(id, 0);
            boolean isDrawing = id.equals(currentDrawerId);
            boolean guessed = correctGuessers.contains(id);
            result.add(new PlayerDto(id, name, score, isDrawing, guessed));
        }
        return result;
    }


    public void addStroke(DrawBatchMessage stroke) {
        strokeHistory.add(stroke);
    }

    public void clearStrokes() {
        strokeHistory.clear();
    }

    public List<DrawBatchMessage> getStrokeHistorySnapshot() {
        return new ArrayList<>(strokeHistory);
    }


    public void markDisconnected(String playerId) {
        disconnectedPlayers.add(playerId);
    }

    public void markReconnected(String playerId) {
        disconnectedPlayers.remove(playerId);
        ScheduledFuture<?> pending = pendingRemovals.remove(playerId);
        if (pending != null) pending.cancel(false);
    }

    public boolean isDisconnected(String playerId) {
        return disconnectedPlayers.contains(playerId);
    }
}