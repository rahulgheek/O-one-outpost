package com.aegis.code_sandbox.dto;

import com.aegis.code_sandbox.judge.Verdict;

/**
 * Full judge result for one submission.
 *
 * <p>{@link #plainTextBody()} preserves the legacy plain-text contract used by
 * arena-service and assessment-service. The remaining fields are returned by
 * the JSON endpoint for dashboards, auditing and debugging.</p>
 */
public record ExecutionResult(
        Verdict verdict,
        String message,
        long timeMs,
        long wallTimeMs,
        long cpuTimeMs,
        long memoryUsedKb,
        long memoryLimitKb,
        int exitCode,
        String stdout,
        String stderr,
        boolean outputTruncated,
        Integer passedTests,
        Integer totalTests
) {

    public String plainTextBody() {
        return switch (verdict) {
            case CE -> {
                String compilerOutput = firstNonBlank(stderr, stdout, message, "Compilation failed.");
                if (outputTruncated && !compilerOutput.contains("truncated")) {
                    compilerOutput += "\n[compiler output truncated by the sandbox]";
                }
                yield "COMPILATION_ERROR\n" + compilerOutput;
            }
            case TLE -> "TIME_LIMIT_EXCEEDED";
            case MLE -> "MEMORY_LIMIT_EXCEEDED";
            case RE -> {
                // A harness can report a per-test RUNTIME_ERROR and still exit 0
                // (assessment-service catches each test). In that case the raw
                // output must survive so partial credit can be parsed.
                if (exitCode == 0) {
                    String output = stdout == null ? "" : stdout.strip();
                    yield output.isEmpty() ? "RUNTIME_ERROR\n"
                            + firstNonBlank(stderr, message, "Runtime error.") : output;
                }
                yield "RUNTIME_ERROR\n"
                        + firstNonBlank(stderr, stdout, message, "Runtime error.");
            }
            case IE -> "SYSTEM_ERROR|"
                    + firstNonBlank(message, "Internal judge error.");
            case AC, WA -> {
                String output = stdout == null ? "" : stdout.strip();
                yield output.isEmpty() ? "(no output)" : output;
            }
        };
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return "";
    }
}
