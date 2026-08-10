package com.scribble.backend.security;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class RateLimiter {

    private final int maxRequests;
    private final long windowMillis;
    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Long>> requestLog = new ConcurrentHashMap<>();

    public RateLimiter(int maxRequests , long windowMillis){
        this.maxRequests = maxRequests;
        this.windowMillis = windowMillis;
    }

    public boolean allow(String key){
        long now = System.currentTimeMillis();
        ConcurrentLinkedDeque<Long> timestamps = requestLog.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());

        synchronized (timestamps){
            while(! timestamps.isEmpty() && now - timestamps.peekFirst() > windowMillis){
                timestamps.pollFirst();
            }
            if(timestamps.size() >= maxRequests){
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }


}
