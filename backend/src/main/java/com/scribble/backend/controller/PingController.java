package com.scribble.backend.controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class PingController {

    @GetMapping("/api/ping")
    public Map<String,Object> ping(){
        return Map.of(
                "message","pong from Spring Boot",
                "timestamp", Instant.now().toString()
        );
    }

}
