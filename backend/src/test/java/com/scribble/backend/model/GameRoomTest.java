package com.scribble.backend.model;

import com.scribble.backend.dto.DrawBatchMessage;
import com.scribble.backend.dto.PlayerDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameRoomTest {

    private GameRoom room;

    @BeforeEach
    void setUp() {
        // A fresh room before every single test, so tests never leak state into each other.
        room = new GameRoom("ABC123");
    }

    @Test
    void maskedWord_hidesAllLettersWhenNothingRevealed() {
        room.setCurrentWord("cat");

        assertEquals("_ _ _", room.getMaskedWord());
    }

    @Test
    void maskedWord_showsRevealedLetters() {
        room.setCurrentWord("cat");
        room.revealRandomHint(new SecureRandom(), 3); // reveal one letter

        // We don't know which letter got revealed (it's random), but exactly
        // one underscore should now be a real letter instead of "_ _ _".
        long underscoreCount = room.getMaskedWord().chars().filter(c -> c == '_').count();
        assertEquals(2, underscoreCount);
    }

    @Test
    void maskedWord_keepsNonLetterCharactersVisible() {
        room.setCurrentWord("ice-cream");

        assertTrue(room.getMaskedWord().contains("-"));
    }

    @Test
    void revealRandomHint_neverRevealsMoreThanMaxHints() {
        room.setCurrentWord("elephant");

        int maxHints = 2;
        room.revealRandomHint(new SecureRandom(), maxHints);
        room.revealRandomHint(new SecureRandom(), maxHints);
        boolean thirdRevealHappened = room.revealRandomHint(new SecureRandom(), maxHints);

        assertFalse(thirdRevealHappened, "Should not be able to reveal a 3rd hint when max is 2");
    }

    @Test
    void setHostIfAbsent_firstPlayerBecomesHost() {
        room.setHostIfAbsent("player-1");

        assertTrue(room.isHost("player-1"));
    }

    @Test
    void setHostIfAbsent_doesNotOverwriteExistingHost() {
        room.setHostIfAbsent("player-1");
        room.setHostIfAbsent("player-2"); // should be ignored, host already set

        assertTrue(room.isHost("player-1"));
        assertFalse(room.isHost("player-2"));
    }

    @Test
    void reassignHostIfNeeded_picksAnotherPlayerWhenHostLeaves() {
        room.getPlayers().put("player-1", "Alice");
        room.getPlayers().put("player-2", "Bob");
        room.setHostIfAbsent("player-1");

        room.getPlayers().remove("player-1");
        room.reassignHostIfNeeded("player-1");

        assertTrue(room.isHost("player-2"));
    }

    @Test
    void reassignHostIfNeeded_doesNothingIfLeavingPlayerWasNotHost() {
        room.getPlayers().put("player-1", "Alice");
        room.getPlayers().put("player-2", "Bob");
        room.setHostIfAbsent("player-1");

        room.getPlayers().remove("player-2");
        room.reassignHostIfNeeded("player-2"); // player-2 wasn't the host

        assertTrue(room.isHost("player-1"), "Host should not change when a non-host leaves");
    }

    @Test
    void removeFromTurnOrder_adjustsCurrentTurnIndexCorrectly() {
        room.getTurnOrder().addAll(List.of("p1", "p2", "p3"));
        room.setCurrentTurnIndex(2); // pointing at p3

        room.removeFromTurnOrder("p1"); // remove someone before the current index

        assertEquals(1, room.getCurrentTurnIndex(), "Index should shift left by one");
        assertEquals(List.of("p2", "p3"), room.getTurnOrder());
    }

    @Test
    void removeFromTurnOrder_wrapsIndexBackToZeroIfNowOutOfBounds() {
        room.getTurnOrder().addAll(List.of("p1", "p2"));
        room.setCurrentTurnIndex(1); // pointing at p2

        room.removeFromTurnOrder("p2"); // remove the player we're currently pointing at

        assertEquals(0, room.getCurrentTurnIndex());
    }

    @Test
    void toPlayerDtos_reflectsScoreAndDrawerStatusCorrectly() {
        room.getPlayers().put("p1", "Alice");
        room.getScores().put("p1", 150);
        room.setCurrentDrawerId("p1");

        List<PlayerDto> dtos = room.toPlayerDtos();

        assertEquals(1, dtos.size());
        PlayerDto alice = dtos.get(0);
        assertEquals("Alice", alice.name());
        assertEquals(150, alice.score());
        assertTrue(alice.isDrawing());
    }

    @Test
    void clearStrokes_removesAllDrawnStrokes() {
        room.addStroke(new DrawBatchMessage(List.of(), "#000000", 4));
        assertEquals(1, room.getStrokeHistorySnapshot().size());

        room.clearStrokes();

        assertEquals(0, room.getStrokeHistorySnapshot().size());
    }

    @Test
    void markDisconnected_thenReconnected_clearsDisconnectedStatus() {
        room.markDisconnected("p1");
        assertTrue(room.isDisconnected("p1"));

        room.markReconnected("p1");

        assertFalse(room.isDisconnected("p1"));
    }
}