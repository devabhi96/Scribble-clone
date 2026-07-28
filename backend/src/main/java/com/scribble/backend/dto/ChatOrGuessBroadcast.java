package com.scribble.backend.dto;

public record ChatOrGuessBroadcast(String playerName, String message, boolean wasCorrectGuess) {}