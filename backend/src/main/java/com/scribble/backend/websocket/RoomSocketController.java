package com.scribble.backend.websocket;

import com.scribble.backend.dto.GameStateMessage;
import com.scribble.backend.dto.JoinMessage;
import com.scribble.backend.dto.PlayerListMessage;
import com.scribble.backend.dto.StrokeHistorySyncMessage;
import com.scribble.backend.model.GameRoom;
import com.scribble.backend.service.RoomService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class RoomSocketController {

    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;

    public RoomSocketController(RoomService roomService, SimpMessagingTemplate messagingTemplate) {
        this.roomService = roomService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/room/{roomCode}/join")
    public void handleJoin(@DestinationVariable String roomCode,
                           JoinMessage message,
                           @Header("simpSessionId") String sessionId) {

        GameRoom room = roomService.getRoom(roomCode.toUpperCase());
        if (room == null) return;

        roomService.registerSession(sessionId, roomCode.toUpperCase(), message.playerId());
        boolean wasReconnect = roomService.joinRoom(roomCode.toUpperCase(), message.playerId(), message.playerName());

        room.withLock(() -> messagingTemplate.convertAndSend(
                "/topic/room/" + roomCode.toUpperCase() + "/players",
                new PlayerListMessage(room.toPlayerDtos())
        ));

        if (wasReconnect) {
            sendSyncToPlayer(room, message.playerId());
        }
    }

    private void sendSyncToPlayer(GameRoom room, String playerId) {
        room.withLock(() -> {
            GameStateMessage state = new GameStateMessage(
                    room.getState().name(),
                    room.getMaskedWord(),
                    room.getCurrentDrawerId(),
                    room.getTimeRemainingSeconds()
            );
            messagingTemplate.convertAndSendToUser(playerId, "/queue/state-sync", state);

            StrokeHistorySyncMessage strokes = new StrokeHistorySyncMessage(room.getStrokeHistorySnapshot());
            messagingTemplate.convertAndSendToUser(playerId, "/queue/sync", strokes);
        });
    }
}