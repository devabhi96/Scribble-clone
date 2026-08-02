package com.scribble.backend.dto;

import java.util.List;

public record StrokeHistorySyncMessage(List<DrawBatchMessage> strokes) {}