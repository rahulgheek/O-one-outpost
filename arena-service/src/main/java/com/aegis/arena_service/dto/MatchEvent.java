package com.aegis.arena_service.dto;

public record MatchEvent(
        String playerId,
        String status,
        int passedCount,
        int totalCount
) {}