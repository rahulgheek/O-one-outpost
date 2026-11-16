package com.aegis.auth_service.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Value("${ANALYTICS_SERVICE_URL:http://localhost:8082}")
    private String analyticsUrl;

    @Value("${internal.secret:}")
    private String internalSecret;

    private void pushAuditLog(String level, String message, Exception ex) {
        executor.submit(() -> {
            try {
                String stackTrace = ex != null ? ex.toString() : null;
                Map<String, Object> payload = Map.of(
                        "serviceName", "auth-service",
                        "logLevel", level,
                        "message", message != null ? message : "Unknown error",
                        "stackTrace", stackTrace != null ? stackTrace : "",
                        "timestamp", Instant.now().toString()
                );
                HttpHeaders headers = new HttpHeaders();
                if (internalSecret != null && !internalSecret.isBlank()) {
                    headers.set("X-Internal-Token", internalSecret);
                }
                restTemplate.postForEntity(
                        analyticsUrl + "/api/analytics/audit",
                        new HttpEntity<>(payload, headers),
                        Void.class);
            } catch (Exception ignored) {}
        });
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException ex) {
        String exClass = ex.getClass().getSimpleName();
        String exMsg = ex.getMessage() != null ? ex.getMessage() : "";
        if (exClass.contains("NoResourceFoundException") || exMsg.contains("No static resource")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Resource not found"));
        }
        log.error("Runtime exception occurred: ", ex);
        pushAuditLog("WARN", ex.getMessage(), ex);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneral(Exception ex) {
        String exClass = ex.getClass().getSimpleName();
        String exMsg = ex.getMessage() != null ? ex.getMessage() : "";
        if (exClass.contains("NoResourceFoundException") || exMsg.contains("No static resource")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Resource not found"));
        }
        log.error("Internal server error: ", ex);
        pushAuditLog("ERROR", ex.getMessage(), ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "An unexpected error occurred. Please try again later."));
    }
}
