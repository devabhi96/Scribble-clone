package com.scribble.backend.config;

import com.scribble.backend.security.RateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfig {

    @Bean
    public RateLimiter authRateLimiter(){
        return new RateLimiter(5,60_000);
    }

    @Bean
    public RateLimiter guessRateLimiter(){
        return new RateLimiter(5,3_000);
    }
}
