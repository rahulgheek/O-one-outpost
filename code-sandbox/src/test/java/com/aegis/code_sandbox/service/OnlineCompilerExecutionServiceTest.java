package com.aegis.code_sandbox.service;

import com.aegis.code_sandbox.config.JudgeProperties;
import com.aegis.code_sandbox.dto.ExecutionRequest;
import com.aegis.code_sandbox.dto.ExecutionResult;
import com.aegis.code_sandbox.judge.Verdict;
import com.aegis.code_sandbox.online.OnlineCompilerClient;
import com.aegis.code_sandbox.online.OnlineCompilerException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OnlineCompilerExecutionServiceTest {

    private static final String CODE = "public class Main { public static void main(String[] a) {} }";

    private final OnlineCompilerClient client = mock(OnlineCompilerClient.class);
    private final JudgeProperties properties = properties();
    private final OnlineCompilerExecutionService service =
            new OnlineCompilerExecutionService(client, properties);

    @Test
    void acceptsHarnessOutputAndReportsProviderMetrics() {
        when(client.run(anyString(), anyString(), anyInt(), anyInt())).thenReturn(response(
                "PROGRESS|3/3\nALL_TESTS_PASSED\n", "", "success", 0, null, false,
                "0.250", "0.400", "240000"));

        ExecutionResult result = service.execute(request(null), 2_000, 256);

        assertEquals(Verdict.AC, result.verdict());
        assertEquals(250, result.timeMs());
        assertEquals(400, result.wallTimeMs());
        assertEquals(-1, result.cpuTimeMs());
        assertEquals(240_000, result.memoryUsedKb());
        assertEquals(262_144, result.memoryLimitKb());
        assertEquals(3, result.passedTests());
        assertEquals(3, result.totalTests());
        assertEquals(0, result.exitCode());
        verify(client).run(eq(CODE), eq(""), eq(2_000), eq(512));
    }

    @Test
    void providerTimeoutIsATimeLimitExceededVerdict() {
        when(client.run(anyString(), anyString(), anyInt(), anyInt())).thenReturn(response(
                "", "Time limit exceeded", "error", 124, null, false,
                "1.000", "1.100", "230000"));

        ExecutionResult result = service.execute(request(null), 1_000, 256);

        assertEquals(Verdict.TLE, result.verdict());
        assertEquals(1_000, result.timeMs());
        assertEquals(1_100, result.wallTimeMs());
    }

    @Test
    void providerMemoryKillIsAMemoryLimitExceededVerdict() {
        when(client.run(anyString(), anyString(), anyInt(), anyInt())).thenReturn(response(
                "", "Memory limit exceeded: the program used 550 MB", "error", 137, 9, false,
                "0.500", "0.600", "563200"));

        ExecutionResult result = service.execute(request(null), 2_000, 256);

        assertEquals(Verdict.MLE, result.verdict());
        assertEquals(563_200, result.memoryUsedKb());
        assertTrue(result.message().contains("256MB"));
    }

    @Test
    void compilerDiagnosticsAreACompilationError() {
        when(client.run(anyString(), anyString(), anyInt(), anyInt())).thenReturn(response(
                "", "Main.java:3: error: illegal start of expression", "error", 1, null, false,
                "0.100", "0.120", "230000"));

        ExecutionResult result = service.execute(request(null), 2_000, 256);

        assertEquals(Verdict.CE, result.verdict());
        assertEquals(0, result.timeMs());
        assertEquals("COMPILATION_ERROR\nMain.java:3: error: illegal start of expression",
                result.plainTextBody());
    }

    @Test
    void ordinaryNonZeroExitIsARuntimeError() {
        when(client.run(anyString(), anyString(), anyInt(), anyInt())).thenReturn(response(
                "", "Exception in thread \"main\" java.lang.IllegalStateException: boom",
                "error", 1, null, false, "0.200", "0.220", "230000"));

        ExecutionResult result = service.execute(request(null), 2_000, 256);

        assertEquals(Verdict.RE, result.verdict());
        assertTrue(result.stderr().contains("IllegalStateException"));
    }

    @Test
    void harnessWrongAnswerIsPreservedWithPartialCounts() {
        when(client.run(anyString(), anyString(), anyInt(), anyInt())).thenReturn(response(
                "PROGRESS|1/2\nWRONG_ANSWER|Test Case 2 Failed.\n", "", "success", 0, null, false,
                "0.300", "0.320", "240000"));

        ExecutionResult result = service.execute(request(null), 2_000, 256);

        assertEquals(Verdict.WA, result.verdict());
        assertEquals(1, result.passedTests());
        assertEquals(2, result.totalTests());
        assertTrue(result.plainTextBody().contains("WRONG_ANSWER"));
    }

    @Test
    void compactProviderTimeIsRoundedToMilliseconds() {
        when(client.run(anyString(), anyString(), anyInt(), anyInt())).thenReturn(response(
                "ALL_TESTS_PASSED", "", "success", 0, null, false,
                "0.0248", "0.0330", "8192"));

        ExecutionResult result = service.execute(request(null), 2_000, 256);

        assertEquals(25, result.timeMs());
        assertEquals(33, result.wallTimeMs());
    }

    @Test
    void inputIsForwardedToTheProvider() {
        when(client.run(anyString(), anyString(), anyInt(), anyInt())).thenReturn(response(
                "hello", "", "success", 0, null, false, "0.010", "0.020", "8192"));

        ExecutionResult result = service.execute(request("World"), 2_000, 256);

        assertEquals(Verdict.AC, result.verdict());
        verify(client).run(eq(CODE), eq("World"), eq(2_000), eq(512));
    }

    @Test
    void oversizedCodeIsRejectedBeforeCallingTheProvider() {
        properties.getOnlineCompiler().setMaxCodeBytes(10);

        ExecutionResult result = service.execute(request(null), 2_000, 256);

        assertEquals(Verdict.CE, result.verdict());
        assertTrue(result.message().contains("10 bytes"));
        verify(client, never()).run(anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    void providerRateLimitBecomesAnInternalError() {
        when(client.run(anyString(), anyString(), anyInt(), anyInt()))
                .thenThrow(new OnlineCompilerException(429, "at capacity"));

        ExecutionResult result = service.execute(request(null), 2_000, 256);

        assertEquals(Verdict.IE, result.verdict());
        assertTrue(result.message().contains("capacity"));
        assertNull(result.passedTests());
    }

    private static ExecutionRequest request(String input) {
        return new ExecutionRequest(CODE, 2_000, 262_144, input);
    }

    private static OnlineCompilerClient.RunResponse response(
            String output,
            String error,
            String status,
            int exitCode,
            Integer signal,
            boolean truncated,
            String time,
            String total,
            String memory
    ) {
        return new OnlineCompilerClient.RunResponse(
                output, error, status, exitCode, signal, truncated, time, total, memory,
                BigDecimal.TWO, 512);
    }

    private static JudgeProperties properties() {
        JudgeProperties properties = new JudgeProperties();
        JudgeProperties.OnlineCompiler online = properties.getOnlineCompiler();
        online.setApiKey("test-key");
        online.setMaxMemoryMb(512);
        online.setMemoryHeadroomMb(256);
        online.setMaxCodeBytes(100_000);
        online.setMaxInputBytes(5_242_880);
        return properties;
    }
}
