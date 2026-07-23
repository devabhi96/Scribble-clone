package com.scribble.backend.model;

import lombok.Getter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class GameRoom {

    private final String roomCode;
    private final Map<String,String> players = new ConcurrentHashMap<>();
    public GameRoom(String roomCode){
        this.roomCode = roomCode;
    }

}
