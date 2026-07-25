package com.scribble.backend.model;

import lombok.Getter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Getter
public class GameRoom {

    private final String roomCode;
    private final Map<String,String> players = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    public GameRoom(String roomCode){
        this.roomCode = roomCode;
    }

    public void withLock(Runnable action){
        lock.lock();
        try{
            action.run();
        }
        finally{
            lock.unlock();
        }
    }

}
