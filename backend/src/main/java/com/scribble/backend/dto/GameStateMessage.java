package com.scribble.backend.dto;

public record GameStateMessage (
        String state,
        String maskedWord,
        String currentDrawerId,
        int timeRemainingSeconds,
        int currentRound,
        int totalRounds,
        boolean infiniteRounds,
        String revealedWord

){}