package com.aegis.code_sandbox.dto;

/**
 * Request from arena-service / assessment-service.
 *
 * <p>Null limits are resolved by the sandbox from DEFAULT_TIME_LIMIT_MS /
 * DEFAULT_MEMORY_LIMIT_MB. Do not hard-code defaults here: the judge must be
 * the single source of truth and must clamp limits to MAX_*.</p>
 *
 * <p>{@code input} is optional standard input for a plain program. The arena
 * and assessment harnesses embed their test data in the generated source, so
 * they normally leave it null.</p>
 */
public record ExecutionRequest(
        String code,
        Integer timeLimitMs,
        Integer memoryLimitKb,
        String input
) {

    /** Backwards-compatible constructor for callers that do not send stdin. */
    public ExecutionRequest(String code, Integer timeLimitMs, Integer memoryLimitKb) {
        this(code, timeLimitMs, memoryLimitKb, null);
    }
}
