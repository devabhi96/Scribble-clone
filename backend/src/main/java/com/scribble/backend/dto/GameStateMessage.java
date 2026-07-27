package com.scribble.backend.dto;

public record GameStateMessage (
    String state,
    String maskedWord,
    String currentDrawerId
){}
