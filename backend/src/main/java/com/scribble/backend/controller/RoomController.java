package com.scribble.backend.controller;

import com.scribble.backend.model.GameRoom;
import com.scribble.backend.service.RoomService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
@RestController
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }
    @PostMapping("/api/rooms")
    public Map<String,String> createRoom(){
        GameRoom room = roomService.createRoom();
        return Map.of("roomCode",room.getRoomCode());
    }
}
