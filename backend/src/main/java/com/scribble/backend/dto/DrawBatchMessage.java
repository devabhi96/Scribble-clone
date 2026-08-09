package com.scribble.backend.dto;

import java.util.List;

public record DrawBatchMessage(List<Point> points, String color, int brushSize) {
}