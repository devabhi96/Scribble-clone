package com.scribble.backend.dto;

import java.util.List;

public record PlayerListMessage(List<PlayerDto> players, String hostPlayerId) {}