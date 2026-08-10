package com.scribble.backend.controller;

import com.scribble.backend.model.GameRoom;
import com.scribble.backend.service.RoomService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping
    public Map<String, String> createRoom() {
        GameRoom room = roomService.createRoom();
        return Map.of("roomCode", room.getRoomCode());
    }

    @GetMapping("/{roomCode}/players")
    public ResponseEntity<?> getPlayers(@PathVariable String roomCode) {
        GameRoom room = roomService.getRoom(roomCode.toUpperCase());
        if (room == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Room not found"));
        }
        List<String> playerNames = new ArrayList<>();
        room.withLock(() -> playerNames.addAll(room.getPlayers().values()));
        return ResponseEntity.ok(Map.of("players", playerNames));
    }
}