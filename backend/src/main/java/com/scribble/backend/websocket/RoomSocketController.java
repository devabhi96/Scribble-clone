package com.scribble.backend.websocket;

import com.scribble.backend.dto.JoinMessage;
import com.scribble.backend.dto.PlayerListMessage;
import com.scribble.backend.model.GameRoom;
import com.scribble.backend.service.RoomService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Collection;
import java.util.Map;

@Controller
public class RoomSocketController {

    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;

    public RoomSocketController(RoomService roomService, SimpMessagingTemplate messagingTemplate) {
        this.roomService = roomService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/room/{roomCode}/join")
    public void handleJoin(@DestinationVariable String roomCode, JoinMessage message) {
        GameRoom room = roomService.getRoom(roomCode.toUpperCase());
        if (room == null) return; // room doesn't exist, silently ignore for now

        roomService.joinRoom(roomCode.toUpperCase(), message.playerName());

        Collection<String> playerNames = room.getPlayers().values();
        messagingTemplate.convertAndSend(
                "/topic/room/" + roomCode.toUpperCase() + "/players",
                new PlayerListMessage(playerNames)
        );

    }
}