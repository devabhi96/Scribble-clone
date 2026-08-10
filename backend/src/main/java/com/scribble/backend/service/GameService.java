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
import com.scribble.backend.security.RateLimiter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GameService {
    private static final int ROUND_DURATION_SECONDS = 60;
    private static final int CHOOSE_WORD_DURATION_SECONDS = 15;
    private static final int HINT_INTERVAL_SECONDS = 15;
    private static final int DRAWER_DISCONNECT_GRACE_SECONDS = 10;
    private static final String DRAWER_GRACE_KEY_SUFFIX = ":drawer-grace";

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Map<String, ScheduledFuture<?>> activeTimers = new ConcurrentHashMap<>();
    private final RoomService roomService;
    private final WordBank wordBank;
    private final SimpMessagingTemplate messagingTemplate;
    private final RateLimiter guessRateLimiter;
    private final SecureRandom random = new SecureRandom();

    public GameService(RoomService roomService, WordBank wordBank, SimpMessagingTemplate messagingTemplate,
                       @Qualifier("guessRateLimiter") RateLimiter guessRateLimiter) {
        this.roomService = roomService;
        this.wordBank = wordBank;
        this.messagingTemplate = messagingTemplate;
        this.guessRateLimiter = guessRateLimiter;
    }

    public void startGame(String roomCode, String playerId) {
        GameRoom room = roomService.getRoom(roomCode);
        if (room == null) return;

        room.withLock(() -> {
            if (!room.isHost(playerId)) return;

            // Fix: Check raw total players to bypass stale reconnect flags that block resuming
            if (room.getPlayers().size() < 2) return;

            cancelDrawerGraceTimer(roomCode);

            boolean isResume = (room.getTimeRemainingSeconds() > 0 || room.getCurrentRound() > 0)
                    && room.getState() == GameRoom.GameState.WAITING;

            room.getTurnOrder().clear();
            room.getTurnOrder().addAll(room.getPlayers().keySet());

            if (!isResume) {
                room.setCurrentTurnIndex(0);
                room.setCurrentRound(0);
            } else {
                if (room.getCurrentTurnIndex() >= room.getTurnOrder().size()) {
                    room.setCurrentTurnIndex(0);
                    room.setCurrentRound(room.getCurrentRound() + 1);
                }
            }
            startTurn(room);
        });
    }

    public void updateSettings(String roomCode, String playerId, int totalRounds, boolean infiniteRounds) {
        GameRoom room = roomService.getRoom(roomCode);
        if (room == null) return;

        room.withLock(() -> {
            if (!room.isHost(playerId)) return;
            if (room.getState() == GameRoom.GameState.DRAWING
                    || room.getState() == GameRoom.GameState.CHOOSING_WORD
                    || room.getState() == GameRoom.GameState.ROUND_END) return;

            int clamped = Math.max(1, Math.min(totalRounds, 20));
            room.setTotalRounds(clamped);
            room.setInfiniteRounds(infiniteRounds);

            broadcastState(room);
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

        if (room.isDisconnected(drawerId)) {
            scheduleDrawerGrace(room, drawerId);
        }
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

    private void applyChosenWord(GameRoom room, String chosenWord) {
        room.setCurrentWord(chosenWord);
        room.resetHints();
        room.setState(GameRoom.GameState.DRAWING);
        room.setTimeRemainingSeconds(ROUND_DURATION_SECONDS);

        broadcastState(room);
        sendCurrentWordToDrawer(room);
        startTimer(room);
    }

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

    private void maybeRevealHint(GameRoom room, int remaining) {
        String word = room.getCurrentWord();
        if (word == null) return;

        int elapsed = ROUND_DURATION_SECONDS - remaining;
        if (elapsed <= 0 || elapsed % HINT_INTERVAL_SECONDS != 0) return;

        long letterCount = word.chars().filter(Character::isLetter).count();
        int maxHints = (int) Math.max(1, (letterCount - 1) / 2);

        room.revealRandomHint(random, maxHints);
    }

    private void cancelTimer(String roomCode) {
        ScheduledFuture<?> existing = activeTimers.remove(roomCode);
        if (existing != null) existing.cancel(false);
    }

    private void broadcastState(GameRoom room) {
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
        if (guessText == null || guessText.isBlank()) return;
        if (guessText.length() > 200) return; // reject absurdly long payloads outright
        if (!guessRateLimiter.allow(playerId)) return; // too many guesses too fast — silently drop

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

                checkRoundCompletion(room);
            } else {
                messagingTemplate.convertAndSend(
                        "/topic/room/" + roomCode + "/chat",
                        new ChatOrGuessBroadcast(playerName, guessText, false)
                );
            }
        });
    }

    private void checkRoundCompletion(GameRoom room) {
        int activePlayersCount = (int) room.getPlayers().keySet().stream()
                .filter(id -> !room.isDisconnected(id))
                .count();

        int totalGuessers = activePlayersCount - 1;

        if (room.getCorrectGuessers().size() >= totalGuessers && totalGuessers > 0) {
            endRoundEarly(room);
        }
    }

    private void endRoundEarly(GameRoom room) {
        cancelTimer(room.getRoomCode());
        room.setState(GameRoom.GameState.ROUND_END);
        room.resetCorrectGuessers();
        broadcastState(room);
        scheduler.schedule(() -> room.withLock(() -> advanceTurn(room)), 3, TimeUnit.SECONDS);
    }

    private void advanceTurn(GameRoom room){
        cancelDrawerGraceTimer(room.getRoomCode());
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

    public void handlePlayerDisconnected(String roomCode, String playerId) {
        GameRoom room = roomService.getRoom(roomCode);
        if (room == null) return;

        room.withLock(() -> {
            long activePlayers = room.getPlayers().keySet().stream()
                    .filter(id -> !room.isDisconnected(id))
                    .count();

            boolean gameInProgress = room.getState() != GameRoom.GameState.WAITING
                    && room.getState() != GameRoom.GameState.GAME_OVER;

            if (activePlayers < 2 && gameInProgress) {
                cancelTimer(room.getRoomCode());
                cancelDrawerGraceTimer(roomCode);
                room.setState(GameRoom.GameState.WAITING);
                room.setCurrentDrawerId(null);
                room.setCurrentWord(null);
                room.clearStrokes();
                room.resetHints();
                broadcastState(room);

                messagingTemplate.convertAndSend(
                        "/topic/room/" + roomCode + "/chat",
                        new ChatOrGuessBroadcast("System", "Not enough players! Game paused.", false)
                );
                return;
            }

            boolean isDrawer = playerId.equals(room.getCurrentDrawerId());
            boolean roundActive = room.getState() == GameRoom.GameState.DRAWING
                    || room.getState() == GameRoom.GameState.CHOOSING_WORD;

            if (isDrawer && roundActive) {
                messagingTemplate.convertAndSend(
                        "/topic/room/" + roomCode + "/chat",
                        new ChatOrGuessBroadcast("System", "Drawer disconnected! Skipping in 10s...", false)
                );
                scheduleDrawerGrace(room, playerId);
            } else if (roundActive) {
                checkRoundCompletion(room);
            }
        });
    }

    private void scheduleDrawerGrace(GameRoom room, String playerId) {
        String roomCode = room.getRoomCode();
        String graceKey = roomCode + DRAWER_GRACE_KEY_SUFFIX;

        ScheduledFuture<?> existing = activeTimers.remove(graceKey);
        if (existing != null) existing.cancel(false);

        ScheduledFuture<?> grace = scheduler.schedule(() -> room.withLock(() -> {
            activeTimers.remove(graceKey);

            boolean stillGone = room.isDisconnected(playerId);
            boolean stillTheirTurn = playerId.equals(room.getCurrentDrawerId());
            boolean stillActive = room.getState() == GameRoom.GameState.DRAWING
                    || room.getState() == GameRoom.GameState.CHOOSING_WORD;

            if (stillGone && stillTheirTurn && stillActive) {
                forceSkipTurn(room);
            }
        }), DRAWER_DISCONNECT_GRACE_SECONDS, TimeUnit.SECONDS);

        activeTimers.put(graceKey, grace);
    }

    public void cancelDrawerGraceTimer(String roomCode) {
        ScheduledFuture<?> existing = activeTimers.remove(roomCode + DRAWER_GRACE_KEY_SUFFIX);
        if (existing != null) existing.cancel(false);
    }

    private void forceSkipTurn(GameRoom room) {
        cancelTimer(room.getRoomCode());
        room.setState(GameRoom.GameState.ROUND_END);
        broadcastState(room);
        scheduler.schedule(() -> room.withLock(() -> advanceTurn(room)), 3, TimeUnit.SECONDS);
    }

    public void handlePlayerRemoved(String roomCode, String playerId) {
        GameRoom room = roomService.getRoom(roomCode);
        if (room == null) return;

        room.withLock(() -> {
            room.removeFromTurnOrder(playerId);
            room.getCorrectGuessers().remove(playerId);

            boolean gameInProgress = room.getState() != GameRoom.GameState.WAITING
                    && room.getState() != GameRoom.GameState.GAME_OVER;

            if (room.getPlayers().size() < 2 && gameInProgress) {
                cancelTimer(room.getRoomCode());
                cancelDrawerGraceTimer(roomCode);
                room.setState(GameRoom.GameState.WAITING);
                room.setCurrentDrawerId(null);
                room.setCurrentWord(null);
                room.clearStrokes();
                room.resetHints();
                broadcastState(room);
            } else if (room.getState() == GameRoom.GameState.DRAWING) {
                checkRoundCompletion(room);
            }
        });
    }
}