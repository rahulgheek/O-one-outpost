package com.aegis.code_sandbox.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LenientBooleanConverterTest {

    private final LenientBooleanConverter converter = new LenientBooleanConverter();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void unquotesDoubleAndSingleQuotedValues() {
        assertEquals(Boolean.FALSE, converter.convert("false"));
        assertEquals(Boolean.FALSE, converter.convert("  false  "));
        assertEquals(Boolean.FALSE, converter.convert("\"false\""));
        assertEquals(Boolean.TRUE, converter.convert("'true'"));
        assertEquals(Boolean.TRUE, converter.convert(" \"TRUE\" "));
    }

    @Test
    void stillSupportsTheStandardSpellings() {
        assertEquals(Boolean.TRUE, converter.convert("1"));
        assertEquals(Boolean.FALSE, converter.convert("0"));
        assertEquals(Boolean.TRUE, converter.convert("on"));
        assertEquals(Boolean.FALSE, converter.convert("no"));
        assertEquals(Boolean.TRUE, converter.convert("\"yes\""));
        assertEquals(Boolean.FALSE, converter.convert("'off'"));
    }

    @Test
    void invalidValuesStillFailLoudly() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> converter.convert("\"maybe\""));

        assertTrue(error.getMessage().contains("Invalid boolean value"));
    }

    @Test
    void unmatchedQuotesAreNotSilentlyAccepted() {
        assertThrows(IllegalArgumentException.class, () -> converter.convert("\"false"));
        assertThrows(IllegalArgumentException.class, () -> converter.convert("false'"));
    }

    @Test
    void quotedValuesBindIntoConfigurationProperties() {
        runner.withPropertyValues(
                        "judge.api-only-mode=\"false\"",
                        "judge.enabled=\"true\"")
                .run(context -> {
                    assertNull(context.getStartupFailure(),
                            () -> "binding failed: " + context.getStartupFailure());

                    JudgeProperties properties = context.getBean(JudgeProperties.class);
                    assertTrue(properties.isEnabled());
                    assertFalse(properties.isApiOnlyMode());
                });
    }

    @Test
    void genuinelyInvalidValuesStillAbortStartup() {
        runner.withPropertyValues("judge.api-only-mode=\"maybe\"")
                .run(context -> {
                    assertNotNull(context.getStartupFailure());
                    assertTrue(messagesOf(context.getStartupFailure()).contains("Invalid boolean value"),
                            () -> "unexpected failure: " + context.getStartupFailure());
                });
    }

    private static String messagesOf(Throwable failure) {
        StringBuilder text = new StringBuilder();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            text.append(current.getMessage()).append('\n');
        }
        return text.toString();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JudgeProperties.class)
    static class TestConfiguration {

        @Bean
        LenientBooleanConverter lenientBooleanConverter() {
            return new LenientBooleanConverter();
        }
    }
}
