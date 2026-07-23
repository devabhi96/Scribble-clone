package com.scribble.backend.controller;

import com.scribble.backend.model.GameRoom;
import com.scribble.backend.service.RoomService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Map;
@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping
    public Map<String,String> createRoom(){
        GameRoom room = roomService.createRoom();
        return Map.of("roomCode",room.getRoomCode());
    }

    @PostMapping("/{roomCode}/join")
    public ResponseEntity<?> joinRoom(@PathVariable String roomCode, @RequestBody Map<String, String> body) {
        String playerName = body.get("playerName");
        if (playerName == null || playerName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "playerName is required"));
        }

        try {
            String playerId = roomService.joinRoom(roomCode.toUpperCase(), playerName);
            return ResponseEntity.ok(Map.of("playerId", playerId, "roomCode", roomCode.toUpperCase()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{roomCode}/players")
    public ResponseEntity<?> getPlayers(@PathVariable String roomCode){
      GameRoom room = roomService.getRoom(roomCode.toUpperCase());
      if(room == null){
          return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error","roomCode not found"));
      }
        Collection<String> playerNames = room.getPlayers().values();
      return ResponseEntity.ok(Map.of("players",playerNames));
    }


}
