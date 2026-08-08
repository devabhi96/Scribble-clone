package com.scribble.backend.dto;

public record TokenResponse(String token, String playerId, String role) {
}