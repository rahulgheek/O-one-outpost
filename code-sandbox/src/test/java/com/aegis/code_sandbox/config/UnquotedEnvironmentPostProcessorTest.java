package com.aegis.code_sandbox.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.Banner;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.support.SystemEnvironmentPropertySourceEnvironmentPostProcessor;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.support.SpringFactoriesLoader;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnquotedEnvironmentPostProcessorTest {

    private static final SpringApplication APPLICATION = new SpringApplication();

    @Test
    void unquotesNumbersBooleansStringsAndPlaceholders() {
        ConfigurableEnvironment environment = environmentWith(
                Map.of(
                        "DEFAULT_MEMORY_LIMIT_MB", "\"256\"",
                        "JUDGE_API_ONLY_MODE", "\"false\"",
                        "ONLINECOMPILER_COMPILER", "'openjdk-25'",
                        "ONLINECOMPILER_API_KEY", "\"test-key\""),
                Map.of());

        process(environment);

        assertEquals("256", environment.getProperty("DEFAULT_MEMORY_LIMIT_MB"));
        // Relaxed environment-name lookup must survive the wrapping.
        assertEquals("256", environment.getProperty("default.memory.limit.mb"));
        assertEquals("false", environment.getProperty("JUDGE_API_ONLY_MODE"));
        assertEquals("openjdk-25", environment.getProperty("ONLINECOMPILER_COMPILER"));
        assertEquals("test-key", environment.getProperty("ONLINECOMPILER_API_KEY"));
        // This is exactly how application.yaml resolves DEFAULT_MEMORY_LIMIT_MB.
        assertEquals("256", environment.resolvePlaceholders("${DEFAULT_MEMORY_LIMIT_MB:256}"));
        assertEquals(256,
                Binder.get(environment)
                        .bind("default.memory.limit.mb", Bindable.of(Integer.class))
                        .get()
                        .intValue());
    }

    @Test
    void normalizesSystemPropertiesAndPreservesTheirPrecedence() {
        ConfigurableEnvironment environment = environmentWith(
                Map.of(
                        "MY_QUOTED_VALUE", "\"from-environment\"",
                        "JUDGE_API_ONLY_MODE", "\"false\""),
                Map.of(
                        "my.quoted.value", "\"from-properties\"",
                        "judge.api-only-mode", "\"true\""));

        process(environment);

        assertEquals("from-properties", environment.getProperty("my.quoted.value"));
        assertEquals("from-environment", environment.getProperty("MY_QUOTED_VALUE"));
        assertEquals("true", environment.getProperty("judge.api-only-mode"));
        assertEquals("false", environment.getProperty("JUDGE_API_ONLY_MODE"));
    }

    @Test
    void trimsValuesButLeavesUnmatchedQuotesAlone() {
        ConfigurableEnvironment environment = environmentWith(
                Map.of(
                        "SPACED", "  256  ",
                        "EMPTY", "\"\"",
                        "LEADING_QUOTE", "\"false",
                        "TRAILING_QUOTE", "false'",
                        "EMBEDDED_QUOTE", "a\"b"),
                Map.of());

        process(environment);

        assertEquals("256", environment.getProperty("SPACED"));
        assertEquals("", environment.getProperty("EMPTY"));
        assertEquals("\"false", environment.getProperty("LEADING_QUOTE"));
        assertEquals("false'", environment.getProperty("TRAILING_QUOTE"));
        assertEquals("a\"b", environment.getProperty("EMBEDDED_QUOTE"));
    }

    @Test
    void isIdempotentAndDoesNotRewrapTheSource() {
        ConfigurableEnvironment environment = environmentWith(
                Map.of("DEFAULT_MEMORY_LIMIT_MB", "\"256\""), Map.of());

        process(environment);
        PropertySource<?> firstSource = environment.getPropertySources().get("systemEnvironment");
        process(environment);

        assertSame(firstSource, environment.getPropertySources().get("systemEnvironment"));
        assertEquals("256", environment.getProperty("DEFAULT_MEMORY_LIMIT_MB"));
    }

    @Test
    void leavesNonHostPropertySourcesUntouched() {
        ConfigurableEnvironment environment = environmentWith(Map.of(), Map.of());
        environment.getPropertySources().addFirst(
                new MapPropertySource("testProperties", Map.of("quoted", "\"keep\"")));

        process(environment);

        assertEquals("\"keep\"", environment.getProperty("quoted"));
    }

    @Test
    @SuppressWarnings("deprecation")
    void isRegisteredAfterBootsEnvironmentWrapperAndBeforeConfigData() {
        List<String> registered = SpringFactoriesLoader.loadFactoryNames(
                EnvironmentPostProcessor.class, getClass().getClassLoader());

        assertTrue(registered.contains(UnquotedEnvironmentPostProcessor.class.getName()),
                () -> "spring.factories did not register the post-processor: " + registered);

        int order = new UnquotedEnvironmentPostProcessor().getOrder();
        assertTrue(order > SystemEnvironmentPropertySourceEnvironmentPostProcessor.DEFAULT_ORDER,
                "must run after Boot replaced systemEnvironment");
        assertTrue(order < ConfigDataEnvironmentPostProcessor.ORDER,
                "must run before profiles, imports and config data are processed");
    }

    @Test
    void quotedSystemPropertiesBindDuringARealApplicationStartup() {
        String previousMemory = System.getProperty("DEFAULT_MEMORY_LIMIT_MB");
        String previousBackend = System.getProperty("JUDGE_EXECUTION_BACKEND");
        String previousApiOnly = System.getProperty("JUDGE_API_ONLY_MODE");
        String previousApiKey = System.getProperty("ONLINECOMPILER_API_KEY");
        System.setProperty("DEFAULT_MEMORY_LIMIT_MB", "\"384\"");
        System.setProperty("JUDGE_EXECUTION_BACKEND", "\"docker\"");
        System.setProperty("JUDGE_API_ONLY_MODE", "\"true\"");
        System.setProperty("ONLINECOMPILER_API_KEY", "\"test-key\"");
        try {
            SpringApplication application = new SpringApplication(StartupTestConfiguration.class);
            application.setWebApplicationType(WebApplicationType.NONE);
            application.setBannerMode(Banner.Mode.OFF);
            application.setLogStartupInfo(false);

            try (ConfigurableApplicationContext context = application.run()) {
                JudgeProperties properties = context.getBean(JudgeProperties.class);

                assertEquals(384, properties.getExecution().getDefaultMemoryLimitMb());
                assertEquals("docker", properties.getBackend());
                assertTrue(properties.isApiOnlyMode());
                assertEquals("test-key", properties.getOnlineCompiler().getApiKey());
            }
        } finally {
            restoreProperty("DEFAULT_MEMORY_LIMIT_MB", previousMemory);
            restoreProperty("JUDGE_EXECUTION_BACKEND", previousBackend);
            restoreProperty("JUDGE_API_ONLY_MODE", previousApiOnly);
            restoreProperty("ONLINECOMPILER_API_KEY", previousApiKey);
        }
    }

    private static ConfigurableEnvironment environmentWith(Map<String, String> environment,
                                                           Map<String, String> systemProperties) {
        ConfigurableEnvironment result = new StandardEnvironment();
        result.getPropertySources().replace("systemEnvironment",
                new SystemEnvironmentPropertySource("systemEnvironment", new LinkedHashMap<>(environment)));
        result.getPropertySources().replace("systemProperties",
                new MapPropertySource("systemProperties", new LinkedHashMap<>(systemProperties)));
        return result;
    }

    private static void process(ConfigurableEnvironment environment) {
        new UnquotedEnvironmentPostProcessor().postProcessEnvironment(environment, APPLICATION);
    }

    private static void restoreProperty(String name, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, previousValue);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JudgeProperties.class)
    static class StartupTestConfiguration {
    }
}
