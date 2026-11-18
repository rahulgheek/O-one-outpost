package com.aegis.code_sandbox.dto;

import com.aegis.code_sandbox.judge.Verdict;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionResultTest {

    @Test
    void timeLimitUsesTheExactLegacyMarker() {
        assertEquals("TIME_LIMIT_EXCEEDED", result(Verdict.TLE, -1, "", "").plainTextBody());
    }

    @Test
    void memoryLimitUsesTheExactLegacyMarker() {
        assertEquals("MEMORY_LIMIT_EXCEEDED", result(Verdict.MLE, 137, "", "").plainTextBody());
    }

    @Test
    void compilationErrorKeepsTheLegacyPrefixAndCompilerMessage() {
        ExecutionResult result = result(Verdict.CE, 1, "", "Main.java:2: error: ';' expected");

        assertTrue(result.plainTextBody().startsWith("COMPILATION_ERROR\n"));
        assertTrue(result.plainTextBody().contains("';' expected"));
    }

    @Test
    void internalErrorUsesTheLegacyMarker() {
        ExecutionResult result = result(Verdict.IE, -1, "", "");

        assertTrue(result.plainTextBody().startsWith("SYSTEM_ERROR|"));
    }

    @Test
    void runtimeErrorWithNonZeroExitIncludesTheStackTrace() {
        ExecutionResult result = result(Verdict.RE, 1, "before crash",
                "Exception in thread \"main\" java.lang.IllegalStateException: boom");

        assertTrue(result.plainTextBody().startsWith("RUNTIME_ERROR\n"));
        assertTrue(result.plainTextBody().contains("IllegalStateException"));
    }

    @Test
    void harnessReportedRuntimeErrorWithExitZeroPreservesRawOutput() {
        String stdout = "PROGRESS|2/5\nASSESSMENT_RESULT|2|5\nRUNTIME_ERROR";
        ExecutionResult result = result(Verdict.RE, 0, stdout, "harness detail");

        assertEquals(stdout, result.plainTextBody());
    }

    @Test
    void successfulRunsReturnRawStdout() {
        String stdout = "PROGRESS|5/5\nALL_TESTS_PASSED";
        assertEquals(stdout, result(Verdict.AC, 0, stdout, "").plainTextBody());
    }

    @Test
    void partialCreditAssessmentOutputIsNotRewritten() {
        String stdout = "PROGRESS|3/5\nASSESSMENT_RESULT|3|5";
        ExecutionResult result = result(Verdict.WA, 0, stdout, "");

        assertEquals(stdout, result.plainTextBody());
        assertEquals(3, result.passedTests());
        assertEquals(5, result.totalTests());
    }

    private static ExecutionResult result(Verdict verdict, int exitCode, String stdout, String stderr) {
        return new ExecutionResult(
                verdict,
                "message",
                12,
                15,
                10,
                4096,
                262_144,
                exitCode,
                stdout,
                stderr,
                false,
                stdout.contains("ASSESSMENT_RESULT|3|5") ? 3 : null,
                stdout.contains("ASSESSMENT_RESULT|3|5") ? 5 : null);
    }
}
