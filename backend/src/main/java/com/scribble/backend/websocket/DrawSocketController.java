package com.scribble.backend.websocket;

import com.scribble.backend.dto.DrawBatchMessage;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class DrawSocketController {

    private final SimpMessagingTemplate messagingTemplate;

    public DrawSocketController(SimpMessagingTemplate messagingTemplate){
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/room/{roomCode}/draw")
    public void handleDraw(@DestinationVariable String roomCode, DrawBatchMessage message){
        messagingTemplate.convertAndSend(
                "/topic/room/" + roomCode.toUpperCase() + "/draw",
                message
        );
    }
}
