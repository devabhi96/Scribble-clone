package com.scribble.backend.model;

import com.scribble.backend.dto.DrawBatchMessage;
import com.scribble.backend.dto.PlayerDto;
import lombok.Getter;
import lombok.Setter;

import java.security.SecureRandom;
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


    private final Set<Integer> revealedHintIndices = new HashSet<>();


    @Setter private String hostPlayerId;


    @Setter private int totalRounds = 3;
    @Setter private boolean infiniteRounds = false;

    private final List<String> currentWordOptions = new ArrayList<>();

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
        if (currentWord == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < currentWord.length(); i++) {
            char c = currentWord.charAt(i);
            if (!Character.isLetter(c)) {
                sb.append(c);
            } else if (revealedHintIndices.contains(i)) {
                sb.append(c);
            } else {
                sb.append('_');
            }
            sb.append(' ');
        }
        return sb.toString().trim();
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



    public void resetHints() {
        revealedHintIndices.clear();
    }


    public boolean revealRandomHint(SecureRandom random, int maxHints) {
        if (currentWord == null) return false;
        if (revealedHintIndices.size() >= maxHints) return false;

        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < currentWord.length(); i++) {
            if (Character.isLetter(currentWord.charAt(i)) && !revealedHintIndices.contains(i)) {
                candidates.add(i);
            }
        }
        if (candidates.isEmpty()) return false;

        int pick = candidates.get(random.nextInt(candidates.size()));
        revealedHintIndices.add(pick);
        return true;
    }


    public void setHostIfAbsent(String playerId) {
        if (hostPlayerId == null) {
            hostPlayerId = playerId;
        }
    }

    public boolean isHost(String playerId) {
        return hostPlayerId != null && hostPlayerId.equals(playerId);
    }


    public void reassignHostIfNeeded(String removedPlayerId) {
        if (removedPlayerId != null && removedPlayerId.equals(hostPlayerId)) {
            hostPlayerId = players.keySet().stream().findFirst().orElse(null);
        }
    }

    public void setWordOptions(List<String> options) {
        currentWordOptions.clear();
        currentWordOptions.addAll(options);
    }

    public List<String> getWordOptionsSnapshot() {
        return new ArrayList<>(currentWordOptions);
    }

    public void removeFromTurnOrder(String playerId) {
        int idx = turnOrder.indexOf(playerId);
        if (idx == -1) return;

        turnOrder.remove(idx);
        if (idx < currentTurnIndex) {
            currentTurnIndex--;
        }
        if (currentTurnIndex >= turnOrder.size()) {
            currentTurnIndex = 0;
        }
    }

}