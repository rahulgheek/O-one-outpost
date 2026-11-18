package com.aegis.code_sandbox.judge;

/**
 * Judge verdicts exposed by the structured execution endpoint.
 *
 * <p>The plain-text endpoint keeps the legacy strings
 * ({@code ALL_TESTS_PASSED}, {@code WRONG_ANSWER}, ...) because arena-service
 * and assessment-service parse them. This enum is the machine-readable view.</p>
 */
public enum Verdict {
    /** Accepted: the program ran and every embedded test passed. */
    AC,
    /** Wrong answer: the program ran, but at least one embedded test failed. */
    WA,
    /** Time limit exceeded (CPU time and/or authoritative wall-clock kill). */
    TLE,
    /** Memory limit exceeded (JVM heap OOM or container OOM kill). */
    MLE,
    /** Runtime error: non-zero exit, uncaught exception, or harness crash. */
    RE,
    /** Compilation error or compiler timeout. */
    CE,
    /** Internal/infrastructure error. The submission was not judged. */
    IE
}
