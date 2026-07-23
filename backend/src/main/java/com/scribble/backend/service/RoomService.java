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



}
