package com.scribble.backend.dto;

public record PlayerDto(String id, String name, int score, boolean isDrawing, boolean hasGuessedCorrectly) {}