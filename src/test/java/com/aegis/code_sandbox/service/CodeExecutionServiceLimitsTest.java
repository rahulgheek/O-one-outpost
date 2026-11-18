package com.aegis.code_sandbox.service;

import com.aegis.code_sandbox.config.JudgeProperties;
import com.aegis.code_sandbox.dto.ExecutionRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CodeExecutionServiceLimitsTest {

    @Test
    void appliesConfiguredDefaultsWhenRequestHasNoLimits() {
        CodeExecutionService service = service(new JudgeProperties());

        CodeExecutionService.Limit limit = service.resolveLimits(new ExecutionRequest("class Main {}", null, null));

        assertEquals(2_000, limit.requestedTimeMs());
        assertEquals(2_000, limit.effectiveTimeMs());
        assertEquals(256, limit.memoryMb());
        assertEquals(262_144, limit.requestedMemoryKb());
    }

    @Test
    void clampsRequestedTimeToConfiguredMaximum() {
        JudgeProperties properties = new JudgeProperties();
        CodeExecutionService service = service(properties);

        CodeExecutionService.Limit limit = service.resolveLimits(new ExecutionRequest("code", 60_000, null));

        assertEquals(10_000, limit.requestedTimeMs());
        assertEquals(10_000, limit.effectiveTimeMs());
    }

    @Test
    void appliesExplicitCalibrationMultiplier() {
        JudgeProperties properties = new JudgeProperties();
        properties.getExecution().setTimeLimitMultiplier(1.5);
        CodeExecutionService service = service(properties);

        CodeExecutionService.Limit limit = service.resolveLimits(new ExecutionRequest("code", 2_000, null));

        assertEquals(2_000, limit.requestedTimeMs());
        assertEquals(3_000, limit.effectiveTimeMs());
    }

    @Test
    void calibrationMultiplierNeverExceedsTheConfiguredMaximum() {
        JudgeProperties properties = new JudgeProperties();
        properties.getExecution().setTimeLimitMultiplier(2.0);
        CodeExecutionService service = service(properties);

        CodeExecutionService.Limit limit = service.resolveLimits(new ExecutionRequest("code", 8_000, null));

        assertEquals(10_000, limit.effectiveTimeMs());
    }

    @Test
    void clampsTinyMemoryRequestsToTheMinimum() {
        CodeExecutionService service = service(new JudgeProperties());

        CodeExecutionService.Limit limit = service.resolveLimits(new ExecutionRequest("code", null, 1_024));

        assertEquals(64, limit.memoryMb());
    }

    @Test
    void clampsLargeMemoryRequestsToTheMaximum() {
        CodeExecutionService service = service(new JudgeProperties());

        CodeExecutionService.Limit limit =
                service.resolveLimits(new ExecutionRequest("code", null, 8 * 1024 * 1024));

        assertEquals(512, limit.memoryMb());
    }

    @Test
    void roundsMemoryKbUpToWholeMegabytes() {
        CodeExecutionService service = service(new JudgeProperties());

        CodeExecutionService.Limit limit =
                service.resolveLimits(new ExecutionRequest("code", null, 200 * 1024 + 1));

        assertEquals(201, limit.memoryMb());
    }

    private static CodeExecutionService service(JudgeProperties properties) {
        return new CodeExecutionService(null, null, null, properties);
    }
}
