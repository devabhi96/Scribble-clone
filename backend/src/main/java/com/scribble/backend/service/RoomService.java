package com.scribble.backend.service;

import com.scribble.backend.model.GameRoom;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomService {
    public static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();

    public record SessionInfo(String roomCode, String playerId) {}
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    public GameRoom createRoom(){
        String code = generateUniqueCode();
        GameRoom room = new GameRoom(code);
        rooms.put(code,room);
        return room;
    }

    public GameRoom getRoom(String code){
        return rooms.get(code);
    }

    private String generateUniqueCode(){
        String code;
        do{
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for(int i =0; i<CODE_LENGTH; i++){
                sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            }
            code = sb.toString();
        }
        while(rooms.containsKey(code));
        return code;
    }

    /** @return true if this playerId was already in the room (i.e. a reconnect) */
    public boolean joinRoom(String roomCode, String playerId, String playerName) {
        GameRoom room = rooms.get(roomCode);
        if (room == null) {
            throw new IllegalArgumentException("Room code not found " + roomCode);
        }

        boolean[] wasAlreadyPresent = {false};
        room.withLock(() -> {
            wasAlreadyPresent[0] = room.getPlayers().containsKey(playerId);
            room.getPlayers().put(playerId, playerName);
            room.setHostIfAbsent(playerId); // first joiner becomes host, if none yet
            if (wasAlreadyPresent[0]) {
                room.markReconnected(playerId);
            }
        });
        return wasAlreadyPresent[0];
    }

    public void registerSession(String sessionId, String roomCode, String playerId) {
        sessions.put(sessionId, new SessionInfo(roomCode, playerId));
    }

    public SessionInfo getSessionInfo(String sessionId) {
        return sessions.get(sessionId);
    }

    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
    }
}