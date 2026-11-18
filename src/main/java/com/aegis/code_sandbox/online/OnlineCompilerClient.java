package com.aegis.code_sandbox.online;

import com.aegis.code_sandbox.config.JudgeProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Client for {@code POST /api/run-code-sync/} on OnlineCompiler.io.
 *
 * <p>The API key is sent raw in the {@code Authorization} header, exactly as
 * documented by the provider. Only 429 (capacity/rate limit) and 503 (queue
 * unavailable) are retried; nothing ran for those responses. A timeout is never
 * retried because the remote program may already have run and been billed.</p>
 */
@Component
public class OnlineCompilerClient {

    private static final Logger log = LoggerFactory.getLogger(OnlineCompilerClient.class);
    private static final String RUN_PATH = "/api/run-code-sync/";
    private static final int MAX_ERROR_CHARS = 600;

    private final JudgeProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Semaphore concurrency;

    @Autowired
    public OnlineCompilerClient(JudgeProperties properties) {
        this(properties, new ObjectMapper());
    }

    /** Package-private seam so tests can supply their own mapper. */
    OnlineCompilerClient(JudgeProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        JudgeProperties.OnlineCompiler config = properties.getOnlineCompiler();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(100, config.getConnectTimeoutMs())))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.concurrency = new Semaphore(Math.max(1, config.getMaxConcurrency()), true);
    }

    public RunResponse run(String code, String input, int timeLimitMs, int providerMemoryMb) {
        JudgeProperties.OnlineCompiler config = properties.getOnlineCompiler();
        String apiKey = config.getApiKey() == null ? "" : config.getApiKey().trim();
        if (apiKey.isEmpty()) {
            throw new OnlineCompilerException(-1,
                    "ONLINECOMPILER_API_KEY is not configured; the sandbox cannot execute submissions.");
        }
        if (code == null || code.isEmpty()) {
            throw new OnlineCompilerException(-1, "No source code was provided.");
        }

        String json = requestJson(code, input, timeLimitMs, providerMemoryMb);
        URI endpoint = URI.create(trimTrailingSlash(config.getBaseUrl()) + RUN_PATH);
        int maxRetries = Math.max(0, config.getMaxRetries());

        for (int attempt = 0; ; attempt++) {
            acquireSlot(config);
            HttpResponse<String> response;
            try {
                HttpRequest request = HttpRequest.newBuilder(endpoint)
                        .timeout(Duration.ofMillis(config.getResponseTimeoutMs()))
                        .header("Authorization", apiKey)
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .header("User-Agent", "AlgoArena-Judge/1.0")
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .build();
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (HttpTimeoutException e) {
                throw new OnlineCompilerException(-1,
                        "OnlineCompiler did not respond within " + config.getResponseTimeoutMs()
                                + "ms. The remote run may still have been counted; check the provider dashboard.",
                        e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new OnlineCompilerException(-1, "Interrupted while waiting for OnlineCompiler.", e);
            } catch (IOException e) {
                throw new OnlineCompilerException(-1,
                        "Could not reach OnlineCompiler at " + endpoint + ": " + rootMessage(e), e);
            } finally {
                concurrency.release();
            }

            int status = response.statusCode();
            if (status == 200) {
                return parseSuccess(response.body());
            }

            String detail = providerError(response.body());
            boolean retryable = status == 429 || status == 503;
            if (retryable && attempt < maxRetries) {
                long delay = retryDelay(config, attempt, response);
                log.warn("OnlineCompiler returned HTTP {} ({}); retrying in {}ms ({}/{}).",
                        status, detail, delay, attempt + 1, maxRetries);
                sleep(delay);
                continue;
            }
            throw new OnlineCompilerException(status, describeHttpFailure(status, detail));
        }
    }

    private String requestJson(String code, String input, int timeLimitMs, int providerMemoryMb) {
        JudgeProperties.OnlineCompiler config = properties.getOnlineCompiler();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("compiler", config.getCompiler());
        body.put("code", code);
        if (input != null && !input.isEmpty()) {
            body.put("input", input);
        }
        // The provider takes seconds. BigDecimal keeps 2500ms as 2.5, not 2.
        body.put("time_limit", BigDecimal.valueOf(Math.max(1, timeLimitMs), 3));
        body.put("memory_limit", providerMemoryMb);
        try {
            return objectMapper.writeValueAsString(body);
        } catch (IOException e) {
            throw new OnlineCompilerException(-1, "Could not serialize the OnlineCompiler request.", e);
        }
    }

    private RunResponse parseSuccess(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null || !root.isObject()) {
                throw new OnlineCompilerException(200, "OnlineCompiler returned an empty or invalid JSON response.");
            }
            return new RunResponse(
                    text(root, "output"),
                    text(root, "error"),
                    text(root, "status"),
                    root.path("exit_code").asInt(-1),
                    root.path("signal").isMissingNode() || root.path("signal").isNull()
                            ? null : root.path("signal").asInt(),
                    root.path("truncated").asBoolean(false),
                    text(root, "time"),
                    text(root, "total"),
                    text(root, "memory"),
                    decimal(root, "time_limit"),
                    integer(root, "memory_limit"));
        } catch (OnlineCompilerException e) {
            throw e;
        } catch (Exception e) {
            throw new OnlineCompilerException(200, "Could not parse the OnlineCompiler response.", e);
        }
    }

    private void acquireSlot(JudgeProperties.OnlineCompiler config) {
        try {
            if (!concurrency.tryAcquire(config.getQueueTimeoutMs(), TimeUnit.MILLISECONDS)) {
                throw new OnlineCompilerException(0,
                        "OnlineCompiler concurrency limit reached locally ("
                                + config.getMaxConcurrency() + " runs in flight). Retry shortly.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OnlineCompilerException(0, "Interrupted while waiting for an execution slot.", e);
        }
    }

    private long retryDelay(JudgeProperties.OnlineCompiler config, int attempt, HttpResponse<String> response) {
        String retryAfter = response.headers().firstValue("Retry-After").orElse(null);
        if (retryAfter != null) {
            try {
                long seconds = Long.parseLong(retryAfter.trim());
                return Math.max(0, Math.min(config.getRetryMaxMs(), seconds * 1_000L));
            } catch (NumberFormatException ignored) {
                // HTTP-date form is not required; use exponential backoff below.
            }
        }
        long exponential = config.getRetryBaseMs() * (1L << Math.min(attempt, 10));
        long capped = Math.min(config.getRetryMaxMs(), exponential);
        return capped + ThreadLocalRandom.current().nextLong(0, 250);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(Math.max(0, millis));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OnlineCompilerException(0, "Interrupted while backing off before an OnlineCompiler retry.", e);
        }
    }

    private String providerError(String body) {
        if (body == null || body.isBlank()) {
            return "no error body";
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            for (String field : new String[]{"error", "message", "detail"}) {
                JsonNode value = root.get(field);
                if (value != null && !value.isNull() && !value.asText().isBlank()) {
                    return truncate(value.asText());
                }
            }
        } catch (Exception ignored) {
            // Fall through to the raw (but bounded) body.
        }
        return truncate(body.strip());
    }

    private static String describeHttpFailure(int status, String detail) {
        if (status == 401 || status == 403) {
            return "OnlineCompiler rejected the API key (HTTP " + status + "): " + detail
                    + ". Check ONLINECOMPILER_API_KEY.";
        }
        if (status == 429) {
            return "OnlineCompiler rate/capacity limit reached (HTTP 429): " + detail
                    + ". Retry later or lower ONLINECOMPILER_MAX_CONCURRENCY.";
        }
        if (status == 413) {
            return "OnlineCompiler rejected the request as too large (HTTP 413): " + detail;
        }
        return "OnlineCompiler returned HTTP " + status + ": " + detail;
    }

    private static String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? "" : node.asText();
    }

    private static BigDecimal decimal(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return node.decimalValue();
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer integer(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? null : node.asInt();
    }

    private static String truncate(String value) {
        String oneLine = value.replaceAll("\\s+", " ").strip();
        return oneLine.length() <= MAX_ERROR_CHARS
                ? oneLine
                : oneLine.substring(0, MAX_ERROR_CHARS) + "...";
    }

    private static String trimTrailingSlash(String value) {
        String trimmed = value == null ? "" : value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    /**
     * Provider response. Time and memory are strings in the API but are parsed
     * defensively so a numeric value also works.
     */
    public record RunResponse(
            String output,
            String error,
            String status,
            int exitCode,
            Integer signal,
            boolean truncated,
            String time,
            String total,
            String memory,
            BigDecimal timeLimitSeconds,
            Integer memoryLimitMb
    ) {
    }
}
