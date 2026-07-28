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
        System.out.println("JOIN RECEIVED: roomCode=" + roomCode + " playerName=" + message.playerName());

        GameRoom room = roomService.getRoom(roomCode.toUpperCase());
        if (room == null) {
            System.out.println("ROOM NOT FOUND: " + roomCode.toUpperCase());
            return;
        }

        roomService.joinRoom(roomCode.toUpperCase(), message.playerId(), message.playerName());

        room.withLock(() -> {
            System.out.println("BROADCASTING PLAYERS: " + room.getPlayers().values());
            messagingTemplate.convertAndSend(
                    "/topic/room/" + roomCode.toUpperCase() + "/players",
                    new PlayerListMessage(room.toPlayerDtos())
            );
        });
    }




}