package com.aegis.assessment_service.dto;

/**
 * Wire format expected by code-sandbox.
 *
 * <p>Field names must match {@code ExecutionRequest} exactly. Jackson
 * serializes record components by name, so the previous
 * {@code timeLimit}/{@code memoryLimit} names were silently ignored and the
 * sandbox fell back to its configured defaults.</p>
 *
 * <p>{@code language} selects the compiler; a missing value means Java for
 * backwards compatibility.</p>
 */
public record SandboxRequest(String code, int timeLimitMs, int memoryLimitKb, String language) {

    /** Backwards-compatible constructor for callers that do not select a language. */
    public SandboxRequest(String code, int timeLimitMs, int memoryLimitKb) {
        this(code, timeLimitMs, memoryLimitKb, null);
    }
}
