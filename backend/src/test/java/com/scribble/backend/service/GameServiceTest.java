package com.scribble.backend.service;

import com.scribble.backend.model.GameRoom;
import com.scribble.backend.security.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock
    private RoomService roomService;

    @Mock
    private WordBank wordBank;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private RateLimiter guessRateLimiter;

    @InjectMocks
    private GameService gameService;

    private GameRoom room;

    @BeforeEach
    void setUp() {
        room = new GameRoom("ROOM1");
        room.getPlayers().put("host-id", "Alice");
        room.getPlayers().put("guest-id", "Bob");
        room.setHostIfAbsent("host-id");

        when(roomService.getRoom("ROOM1")).thenReturn(room);
        // Not every test submits a guess, so this stub is "lenient" — it won't complain
        // about being unused on tests that never reach the rate-limit check.
        lenient().when(guessRateLimiter.allow(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
    }

    @Test
    void startGame_onlyHostCanStartIt() {
        gameService.startGame("ROOM1", "guest-id"); // Bob tries to start, but he's not host

        assertEquals(GameRoom.GameState.WAITING, room.getState(), "Non-host starting the game should be ignored");
    }

    @Test
    void startGame_hostCanStartWithEnoughPlayers() {
        when(wordBank.getRandomOptions(3)).thenReturn(java.util.List.of("cat", "dog", "sun"));

        gameService.startGame("ROOM1", "host-id");

        assertEquals(GameRoom.GameState.CHOOSING_WORD, room.getState());
        // NOTE: players is a ConcurrentHashMap, so turn order is NOT guaranteed to follow
        // join order (that's a separate, real finding — see the write-up). We only assert
        // that a *valid* player was picked as drawer, not which specific one.
        assertTrue(
                room.getCurrentDrawerId().equals("host-id") || room.getCurrentDrawerId().equals("guest-id"),
                "Drawer should be one of the two players in the room"
        );
    }

    @Test
    void startGame_doesNothingWithFewerThanTwoPlayers() {
        room.getPlayers().clear();
        room.getPlayers().put("host-id", "Alice"); // only 1 player now
        room.setHostIfAbsent("host-id");

        gameService.startGame("ROOM1", "host-id");

        assertEquals(GameRoom.GameState.WAITING, room.getState());
    }

    @Test
    void chooseWord_onlyCurrentDrawerCanChooseIt() {
        room.setState(GameRoom.GameState.CHOOSING_WORD);
        room.setCurrentDrawerId("host-id");

        gameService.chooseWord("ROOM1", "guest-id", "cat"); // Bob tries to choose, but he's not the drawer

        assertNull(room.getCurrentWord(), "Word should not be set when a non-drawer tries to choose it");
    }

    @Test
    void chooseWord_drawerChoosingMovesGameToDrawingState() {
        room.setState(GameRoom.GameState.CHOOSING_WORD);
        room.setCurrentDrawerId("host-id");

        gameService.chooseWord("ROOM1", "host-id", "cat");

        assertEquals("cat", room.getCurrentWord());
        assertEquals(GameRoom.GameState.DRAWING, room.getState());
    }

    @Test
    void submitGuess_correctGuessAwardsPointsToGuesserAndDrawer() {
        // Using 3 players here on purpose: with only 2, one correct guess makes
        // checkRoundCompletion() end the round immediately and wipe correctGuessers
        // before we can assert on it. 3 players lets us check the "mid-round" state.
        room.getPlayers().put("extra-id", "Carol");

        room.setState(GameRoom.GameState.DRAWING);
        room.setCurrentDrawerId("host-id");
        room.setCurrentWord("cat");
        room.setTimeRemainingSeconds(60);

        gameService.submitGuess("ROOM1", "guest-id", "cat");

        assertTrue(room.getCorrectGuessers().contains("guest-id"));
        assertTrue(room.getScores().getOrDefault("guest-id", 0) > 0, "Correct guesser should earn points");
        assertTrue(room.getScores().getOrDefault("host-id", 0) > 0, "Drawer should also earn a small bonus");
    }

    @Test
    void submitGuess_drawerCannotGuessTheirOwnWord() {
        room.setState(GameRoom.GameState.DRAWING);
        room.setCurrentDrawerId("host-id");
        room.setCurrentWord("cat");

        gameService.submitGuess("ROOM1", "host-id", "cat");

        assertFalse(room.getCorrectGuessers().contains("host-id"));
    }

    @Test
    void submitGuess_samePlayerCannotScoreTwiceForOneWord() {
        room.setState(GameRoom.GameState.DRAWING);
        room.setCurrentDrawerId("host-id");
        room.setCurrentWord("cat");
        room.setTimeRemainingSeconds(60);

        gameService.submitGuess("ROOM1", "guest-id", "cat"); // correct once
        int scoreAfterFirstGuess = room.getScores().get("guest-id");

        gameService.submitGuess("ROOM1", "guest-id", "cat"); // tries again
        int scoreAfterSecondGuess = room.getScores().get("guest-id");

        assertEquals(scoreAfterFirstGuess, scoreAfterSecondGuess, "Score should not increase from guessing again");
    }

    @Test
    void submitGuess_isIgnoredWhenPlayerIsRateLimited() {
        room.setState(GameRoom.GameState.DRAWING);
        room.setCurrentDrawerId("host-id");
        room.setCurrentWord("cat");

        // Override the default lenient stub: this player has hit their guess limit.
        when(guessRateLimiter.allow("guest-id")).thenReturn(false);

        gameService.submitGuess("ROOM1", "guest-id", "cat");

        assertFalse(room.getCorrectGuessers().contains("guest-id"), "Guess should be dropped when rate-limited, even if correct");
    }

    @Test
    void submitGuess_wrongGuessDoesNotAwardPoints() {
        room.setState(GameRoom.GameState.DRAWING);
        room.setCurrentDrawerId("host-id");
        room.setCurrentWord("cat");

        gameService.submitGuess("ROOM1", "guest-id", "dog");

        assertFalse(room.getCorrectGuessers().contains("guest-id"));
        assertEquals(0, room.getScores().getOrDefault("guest-id", 0));
    }

    @Test
    void updateSettings_onlyHostCanChangeRoundCount() {
        gameService.updateSettings("ROOM1", "guest-id", 5, false); // Bob, not host, tries

        assertEquals(3, room.getTotalRounds(), "Default of 3 should be unchanged");
    }

    @Test
    void updateSettings_clampsRoundsBetweenOneAndTwenty() {
        gameService.updateSettings("ROOM1", "host-id", 999, false);

        assertEquals(20, room.getTotalRounds(), "Should be clamped down to the max of 20");
    }
}