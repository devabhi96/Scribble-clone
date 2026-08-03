package com.scribble.backend.dto;

public record RoomSettingsMessage(String playerId, int totalRounds, boolean infiniteRounds) {}