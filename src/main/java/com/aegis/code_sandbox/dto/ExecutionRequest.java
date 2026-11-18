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
 *
 * <p>{@code language} selects the compiler. It is optional and defaults to
 * Java so existing callers keep their original behaviour. Values are validated
 * against the closed {@link com.aegis.code_sandbox.judge.Language} registry
 * before execution.</p>
 */
public record ExecutionRequest(
        String code,
        Integer timeLimitMs,
        Integer memoryLimitKb,
        String input,
        String language
) {

    /** Backwards-compatible constructor for callers that do not send stdin. */
    public ExecutionRequest(String code, Integer timeLimitMs, Integer memoryLimitKb) {
        this(code, timeLimitMs, memoryLimitKb, null, null);
    }

    /** Backwards-compatible constructor for callers that send stdin but not a language. */
    public ExecutionRequest(String code, Integer timeLimitMs, Integer memoryLimitKb, String input) {
        this(code, timeLimitMs, memoryLimitKb, input, null);
    }
}
