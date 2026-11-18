package com.aegis.code_sandbox.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JudgePropertiesTest {

    @Test
    void onlineCompilerBackendRequiresAnApiKey() {
        JudgeProperties properties = new JudgeProperties();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, properties::validate);

        assertTrue(error.getMessage().contains("ONLINECOMPILER_API_KEY"));
    }

    @Test
    void apiOnlyModeDoesNotRequireAnApiKey() {
        JudgeProperties properties = new JudgeProperties();
        properties.setApiOnlyMode(true);

        assertDoesNotThrow(properties::validate);
    }

    @Test
    void dockerBackendWithoutAnExplicitHostIsRejected() {
        JudgeProperties properties = new JudgeProperties();
        properties.setBackend("docker");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, properties::validate);

        assertTrue(error.getMessage().contains("JUDGE_DOCKER_HOST"));
        assertTrue(error.getMessage().contains("JUDGE_DOCKER_SSH_HOST"));
    }

    @Test
    void dockerBackendWithAnExplicitHostDoesNotRequireAnOnlineCompilerKey() {
        JudgeProperties properties = new JudgeProperties();
        properties.setBackend("docker");
        properties.getDocker().setHost("unix:///var/run/docker.sock");

        assertDoesNotThrow(properties::validate);
    }

    @Test
    void dockerBackendDoesNotRequireAHostInApiOnlyMode() {
        JudgeProperties properties = new JudgeProperties();
        properties.setBackend("docker");
        properties.setApiOnlyMode(true);

        assertDoesNotThrow(properties::validate);
    }

    @Test
    void insecureHttpBaseUrlIsRejectedOutsideLoopbackTests() {
        JudgeProperties properties = new JudgeProperties();
        properties.getOnlineCompiler().setApiKey("test-key");
        properties.getOnlineCompiler().setBaseUrl("http://api.example.com");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, properties::validate);

        assertTrue(error.getMessage().contains("HTTPS"));
    }

    @Test
    void loopbackHttpIsAllowedOnlyWithTheExplicitTestFlag() {
        JudgeProperties properties = new JudgeProperties();
        properties.getOnlineCompiler().setApiKey("test-key");
        properties.getOnlineCompiler().setBaseUrl("http://127.0.0.1:12345");
        properties.getOnlineCompiler().setAllowInsecureHttp(true);

        assertDoesNotThrow(properties::validate);
    }

    @Test
    void onlineCompilerRejectsARunLimitAboveTheProviderCeiling() {
        JudgeProperties properties = new JudgeProperties();
        properties.getOnlineCompiler().setApiKey("test-key");
        properties.getExecution().setMaxTimeLimitMs(30_001);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, properties::validate);

        assertTrue(error.getMessage().contains("30000"));
    }

    @Test
    void onlineCompilerRequiresResponseHeadroomAboveTheRunLimit() {
        JudgeProperties properties = new JudgeProperties();
        properties.getOnlineCompiler().setApiKey("test-key");
        properties.getOnlineCompiler().setResponseTimeoutMs(10_000);
        properties.getExecution().setMaxTimeLimitMs(10_000);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, properties::validate);

        assertTrue(error.getMessage().contains("ONLINECOMPILER_RESPONSE_TIMEOUT_MS"));
    }

    @Test
    void onlineBackendUsesTheProviderCodeLimitWhenItIsLower() {
        JudgeProperties properties = new JudgeProperties();
        properties.getExecution().setMaxCodeBytes(262_144);

        assertEquals(100_000, properties.effectiveMaxCodeBytes());
    }

    @Test
    void dockerBackendKeepsTheLocalCodeLimit() {
        JudgeProperties properties = new JudgeProperties();
        properties.setBackend("docker");
        properties.getExecution().setMaxCodeBytes(262_144);

        assertEquals(262_144, properties.effectiveMaxCodeBytes());
    }
}
