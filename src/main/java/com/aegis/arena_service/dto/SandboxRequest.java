package com.aegis.arena_service.dto;

/**
 * Submission payload from the arena client and, after wrapping, the wire
 * format sent to code-sandbox.
 *
 * <p>{@code language} is the judge language id (java, python, cpp, c,
 * javascript, typescript, go, rust). A missing value means Java for
 * backwards compatibility. The sandbox validates the id against its closed
 * registry and selects the matching compiler.</p>
 */
public record SandboxRequest(
        String code,
        Integer timeLimitMs,
        Integer memoryLimitKb,
        String language
) {

    /** Backwards-compatible constructor for callers that do not select a language. */
    public SandboxRequest(String code, Integer timeLimitMs, Integer memoryLimitKb) {
        this(code, timeLimitMs, memoryLimitKb, null);
    }
}
