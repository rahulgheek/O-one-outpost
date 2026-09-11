package com.aegis.arena_service.dto;

public record SandboxRequest(
        String code,
        Integer timeLimitMs,
        Integer memoryLimitKb
) {}