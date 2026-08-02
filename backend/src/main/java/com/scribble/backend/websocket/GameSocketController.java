package com.scribble.backend.websocket;

import com.scribble.backend.dto.ChooseWordMessage;
import com.scribble.backend.dto.GuessMessage;
import com.scribble.backend.service.GameService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
public class GameSocketController {

    private final GameService gameService;

    public GameSocketController(GameService gameService) {
        this.gameService = gameService;
    }

    @MessageMapping("/room/{roomCode}/start")
    public void handleStartGame(@DestinationVariable String roomCode) {
        gameService.startGame(roomCode.toUpperCase());
    }

    @MessageMapping("/room/{roomCode}/choose-word")
    public void handleChooseWord(@DestinationVariable String roomCode, ChooseWordMessage message) {
        gameService.chooseWord(roomCode.toUpperCase(), message.playerId(), message.chosenWord());
    }

    @MessageMapping("/room/{roomCode}/guess")
    public void handleGuess(@DestinationVariable String roomCode, GuessMessage message) {
        gameService.submitGuess(roomCode.toUpperCase(), message.playerId(), message.text());
    }
}