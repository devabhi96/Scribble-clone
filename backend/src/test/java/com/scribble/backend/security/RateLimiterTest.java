package com.scribble.backend.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    @Test
    void allow_permitsRequestsUpToTheMax() {
        RateLimiter limiter = new RateLimiter(3, 60_000);

        assertTrue(limiter.allow("client-1"));
        assertTrue(limiter.allow("client-1"));
        assertTrue(limiter.allow("client-1"));
    }

    @Test
    void allow_blocksOnceMaxIsExceeded() {
        RateLimiter limiter = new RateLimiter(3, 60_000);

        limiter.allow("client-1");
        limiter.allow("client-1");
        limiter.allow("client-1");

        assertFalse(limiter.allow("client-1"), "4th request within the window should be blocked");
    }

    @Test
    void allow_tracksEachKeyIndependently() {
        RateLimiter limiter = new RateLimiter(1, 60_000);

        assertTrue(limiter.allow("client-A"));
        assertFalse(limiter.allow("client-A"), "client-A is now over its limit");
        assertTrue(limiter.allow("client-B"), "client-B has its own independent limit");
    }

    @Test
    void allow_permitsAgainAfterWindowExpires() throws InterruptedException {
        RateLimiter limiter = new RateLimiter(1, 100); // tiny 100ms window for a fast test

        assertTrue(limiter.allow("client-1"));
        assertFalse(limiter.allow("client-1"), "still within the 100ms window");

        Thread.sleep(150); // let the window slide past

        assertTrue(limiter.allow("client-1"), "window has expired, should be allowed again");
    }
}