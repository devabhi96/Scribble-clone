package com.scribble.backend.dto;

import java.util.List;

public record DrawBatchMessage(String playerId, List<Point> points, String color, int brushSize) {
}