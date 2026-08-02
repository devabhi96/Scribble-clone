package com.scribble.backend.websocket;

import com.scribble.backend.dto.DrawBatchMessage;
import com.scribble.backend.model.GameRoom;
import com.scribble.backend.service.RoomService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class DrawSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final RoomService roomService;

    public DrawSocketController(SimpMessagingTemplate messagingTemplate, RoomService roomService){
        this.messagingTemplate = messagingTemplate;
        this.roomService = roomService;
    }

    @MessageMapping("/room/{roomCode}/draw")
    public void handleDraw(@DestinationVariable String roomCode, DrawBatchMessage message){
        GameRoom room = roomService.getRoom(roomCode.toUpperCase());
        if (room == null) return;

        boolean[] allowed = {false};
        room.withLock(() -> {
            allowed[0] = room.getState() == GameRoom.GameState.DRAWING
                    && message.playerId() != null
                    && message.playerId().equals(room.getCurrentDrawerId());

            if (allowed[0]) {
                room.addStroke(message);
            }
        });

        if (!allowed[0]) return;

        messagingTemplate.convertAndSend(
                "/topic/room/" + roomCode.toUpperCase() + "/draw",
                message
        );
    }
}