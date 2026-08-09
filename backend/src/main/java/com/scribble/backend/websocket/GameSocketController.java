package com.scribble.backend.websocket;

import com.scribble.backend.dto.ChooseWordMessage;
import com.scribble.backend.dto.GuessMessage;
import com.scribble.backend.dto.RoomSettingsMessage;
import com.scribble.backend.service.GameService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class GameSocketController {

    private final GameService gameService;

    public GameSocketController(GameService gameService) {
        this.gameService = gameService;
    }

    @MessageMapping("/room/{roomCode}/start")
    public void handleStartGame(@DestinationVariable String roomCode, Principal principal) {
        gameService.startGame(roomCode.toUpperCase(), principal.getName());
    }

    @MessageMapping("/room/{roomCode}/settings")
    public void handleUpdateSettings(@DestinationVariable String roomCode, RoomSettingsMessage message, Principal principal) {
        if (message == null) return;
        gameService.updateSettings(roomCode.toUpperCase(), principal.getName(), message.totalRounds(), message.infiniteRounds());
    }

    @MessageMapping("/room/{roomCode}/choose-word")
    public void handleChooseWord(@DestinationVariable String roomCode, ChooseWordMessage message, Principal principal) {
        gameService.chooseWord(roomCode.toUpperCase(), principal.getName(), message.chosenWord());
    }

    @MessageMapping("/room/{roomCode}/guess")
    public void handleGuess(@DestinationVariable String roomCode, GuessMessage message, Principal principal) {
        gameService.submitGuess(roomCode.toUpperCase(), principal.getName(), message.text());
    }
}