package com.aegis.code_sandbox.online;

import com.aegis.code_sandbox.config.JudgeProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnlineCompilerClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsTheDocumentedRequestAndParsesTheResponse() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<JsonNode> request = new AtomicReference<>();

        startServer(exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            request.set(objectMapper.readTree(exchange.getRequestBody().readAllBytes()));
            respond(exchange, 200, """
                    {
                      "output": "Hello, World!\\n",
                      "error": "",
                      "status": "success",
                      "exit_code": 0,
                      "signal": null,
                      "truncated": false,
                      "time": "0.250",
                      "total": "0.400",
                      "memory": "240000",
                      "time_limit": 2.5,
                      "memory_limit": 384
                    }
                    """);
        });

        JudgeProperties properties = properties();
        OnlineCompilerClient client = new OnlineCompilerClient(properties, objectMapper);

        OnlineCompilerClient.RunResponse result =
                client.run("class Main { public static void main(String[] a) {} }", "World", 2_500, 384);

        assertEquals("secret-key", authorization.get());
        assertTrue(contentType.get().startsWith("application/json"));
        assertEquals("openjdk-25", request.get().path("compiler").asText());
        assertEquals("World", request.get().path("input").asText());
        assertEquals(0, request.get().path("time_limit").decimalValue().compareTo(new java.math.BigDecimal("2.5")));
        assertEquals(384, request.get().path("memory_limit").asInt());

        assertEquals("Hello, World!\n", result.output());
        assertEquals("success", result.status());
        assertEquals(0, result.exitCode());
        assertEquals("0.250", result.time());
        assertEquals("0.400", result.total());
        assertEquals("240000", result.memory());
        assertEquals(2.5, result.timeLimitSeconds().doubleValue());
        assertEquals(384, result.memoryLimitMb());
    }

    @Test
    void forwardsTheRequestedCompiler() throws Exception {
        AtomicReference<JsonNode> request = new AtomicReference<>();
        startServer(exchange -> {
            request.set(objectMapper.readTree(exchange.getRequestBody().readAllBytes()));
            respond(exchange, 200, "{\"output\":\"ok\",\"status\":\"success\",\"exit_code\":0}");
        });

        new OnlineCompilerClient(properties(), objectMapper)
                .run("print(1)", null, 1_000, 384, "python-3.14");

        assertEquals("python-3.14", request.get().path("compiler").asText());
    }

    @Test
    void rejectsAnInvalidApiKeyWithAUsefulMessage() throws Exception {
        startServer(exchange -> respond(exchange, 401, "{\"error\":\"invalid API key\"}"));

        OnlineCompilerException error = assertThrows(OnlineCompilerException.class,
                () -> new OnlineCompilerClient(properties(), objectMapper)
                        .run("class Main {}", null, 2_000, 512));

        assertEquals(401, error.statusCode());
        assertTrue(error.getMessage().contains("invalid API key"));
        assertTrue(error.getMessage().contains("ONLINECOMPILER_API_KEY"));
    }

    @Test
    void retriesCapacityFailuresButNotBadRequests() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        startServer(exchange -> {
            int call = requests.incrementAndGet();
            if (call == 1) {
                exchange.getResponseHeaders().set("Retry-After", "0");
                respond(exchange, 429, "{\"error\":\"all sandboxes are busy\"}");
            } else {
                respond(exchange, 200, "{\"output\":\"ok\",\"status\":\"success\",\"exit_code\":0}");
            }
        });

        JudgeProperties properties = properties();
        properties.getOnlineCompiler().setMaxRetries(1);
        properties.getOnlineCompiler().setRetryBaseMs(1);
        properties.getOnlineCompiler().setRetryMaxMs(10);

        OnlineCompilerClient.RunResponse result =
                new OnlineCompilerClient(properties, objectMapper).run("class Main {}", null, 2_000, 512);

        assertEquals(2, requests.get());
        assertEquals("ok", result.output());
    }

    @Test
    void doesNotRetryARejectedRequest() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        startServer(exchange -> {
            requests.incrementAndGet();
            respond(exchange, 400, "{\"error\":\"code is required\"}");
        });

        JudgeProperties properties = properties();
        properties.getOnlineCompiler().setMaxRetries(3);

        OnlineCompilerException error = assertThrows(OnlineCompilerException.class,
                () -> new OnlineCompilerClient(properties, objectMapper).run("class Main {}", null, 2_000, 512));

        assertEquals(400, error.statusCode());
        assertEquals(1, requests.get());
    }

    private JudgeProperties properties() {
        JudgeProperties properties = new JudgeProperties();
        JudgeProperties.OnlineCompiler online = properties.getOnlineCompiler();
        online.setApiKey("secret-key");
        online.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        online.setAllowInsecureHttp(true);
        online.setMaxConcurrency(2);
        online.setResponseTimeoutMs(5_000);
        online.setQueueTimeoutMs(2_000);
        online.setMaxRetries(0);
        return properties;
    }

    private void startServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/run-code-sync/", exchange -> {
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
