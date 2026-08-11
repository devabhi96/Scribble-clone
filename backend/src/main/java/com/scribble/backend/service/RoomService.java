package com.scribble.backend.service;

import com.scribble.backend.model.GameRoom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomService {
    private static final Logger log = LoggerFactory.getLogger(RoomService.class);

    public static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;


    private static final int MAX_CONCURRENT_ROOMS = 5000;

    private static final long IDLE_ROOM_MINUTES = 30;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();

    private final Map<String, Instant> lastActivity = new ConcurrentHashMap<>();

    public record SessionInfo(String roomCode, String playerId) {}
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    public GameRoom createRoom(){
        if (rooms.size() >= MAX_CONCURRENT_ROOMS) {
            throw new IllegalStateException("Server is at capacity, please try again shortly");
        }
        String code = generateUniqueCode();
        GameRoom room = new GameRoom(code);
        rooms.put(code, room);
        lastActivity.put(code, Instant.now());
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
        lastActivity.put(roomCode, Instant.now());
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


    @Scheduled(fixedRate = 5, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    public void reapAbandonedRooms() {
        Instant cutoff = Instant.now().minus(IDLE_ROOM_MINUTES, ChronoUnit.MINUTES);
        int removed = 0;
        for (String code : rooms.keySet()) {
            GameRoom room = rooms.get(code);
            if (room == null) continue;
            Instant last = lastActivity.getOrDefault(code, Instant.EPOCH);
            boolean empty = room.getPlayers().isEmpty();
            if (empty && last.isBefore(cutoff)) {
                rooms.remove(code);
                lastActivity.remove(code);
                removed++;
            }
        }
        if (removed > 0) {
            log.info("Reaped {} abandoned room(s). {} room(s) remain.", removed, rooms.size());
        }
    }
}