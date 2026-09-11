package com.aegis.code_sandbox.dto;

public record ExecutionRequest(
        String code,
        Integer timeLimitMs,
        Integer memoryLimitKb
) {
    // Compact constructor to enforce safe default limits
    public ExecutionRequest {
        if (timeLimitMs == null) {
            timeLimitMs = 5000; // Default: 5 seconds
        }
        if (memoryLimitKb == null) {
            memoryLimitKb = 256000; // Default: 256 MB
        }
    }
}