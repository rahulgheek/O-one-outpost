package com.aegis.assessment_service.dto;

/**
 * Wire format expected by code-sandbox.
 *
 * <p>Field names must match {@code ExecutionRequest} exactly. Jackson
 * serializes record components by name, so the previous
 * {@code timeLimit}/{@code memoryLimit} names were silently ignored and the
 * sandbox fell back to its configured defaults.</p>
 */
public record SandboxRequest(String code, int timeLimitMs, int memoryLimitKb) {
}
