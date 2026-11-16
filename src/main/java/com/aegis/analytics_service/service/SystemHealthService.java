package com.aegis.analytics_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class SystemHealthService {

    private final WebClient webClient;

    @Value("${services.urls.auth}")
    private String authUrl;

    @Value("${services.urls.assessment}")
    private String assessmentUrl;

    @Value("${services.urls.arena}")
    private String arenaUrl;

    @Value("${services.urls.sandbox}")
    private String sandboxUrl;

    @Value("${services.urls.gateway}")
    private String gatewayUrl;

    public SystemHealthService(@Value("${internal.secret:}") String internalSecret) {
        WebClient.Builder builder = WebClient.builder();
        if (internalSecret != null && !internalSecret.isBlank()) {
            builder.defaultHeader("X-Internal-Token", internalSecret);
        }
        this.webClient = builder.build();
    }

    public Map<String, String> checkAllServices() {
        Map<String, String> healthStatus = new HashMap<>();
        healthStatus.put("auth-service", pingService(authUrl));
        healthStatus.put("assessment-service", pingService(assessmentUrl));
        healthStatus.put("arena-service", pingService(arenaUrl));
        healthStatus.put("code-sandbox", pingService(sandboxUrl));
        healthStatus.put("gateway-service", pingService(gatewayUrl));
        healthStatus.put("analytics-service", "UP"); // Self
        return healthStatus;
    }

    public Map<String, Integer> getTrafficData() {
        int rps = 0;
        int wsCount = 0;

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> gatewayMetrics = webClient.get()
                    .uri(gatewayUrl + "/api/gateway/metrics")
                    .exchangeToMono(response -> response.bodyToMono(Map.class))
                    .timeout(Duration.ofSeconds(2))
                    .onErrorReturn(new HashMap<>())
                    .block();
            if (gatewayMetrics != null && gatewayMetrics.get("rps") != null) {
                rps = ((Number) gatewayMetrics.get("rps")).intValue();
            }
        } catch (Exception ignored) {}

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> arenaMetrics = webClient.get()
                    .uri(arenaUrl + "/api/arena/metrics")
                    .exchangeToMono(response -> response.bodyToMono(Map.class))
                    .timeout(Duration.ofSeconds(2))
                    .onErrorReturn(new HashMap<>())
                    .block();
            if (arenaMetrics != null && arenaMetrics.get("activeWebSockets") != null) {
                wsCount = ((Number) arenaMetrics.get("activeWebSockets")).intValue();
            }
        } catch (Exception ignored) {}

        return Map.of("requestsPerSecond", rps, "activeWebSockets", wsCount);
    }

    /**
     * Determines if a service is UP or DOWN.
     *
     * KEY FIX: Uses exchangeToMono() instead of retrieve().toBodilessEntity().
     *
     * - retrieve() throws WebClientResponseException for 4xx/5xx responses. If we
     *   then use onErrorResume() to suppress that, we ALSO suppress connection errors
     *   (Connection Refused, Timeout), making everything appear "UP".
     *
     * - exchangeToMono() gives direct access to the raw ClientResponse for ANY valid
     *   HTTP reply (200, 401, 404, 500) without throwing. ONLY genuine network-level
     *   failures (Connection Refused, read timeout) propagate as exceptions.
     *
     * Result: Any HTTP response = UP. Network failure / timeout = DOWN.
     */
    private String pingService(String url) {
        try {
            String result = webClient.get()
                    .uri(url)
                    .exchangeToMono(response -> Mono.just("UP"))
                    .timeout(Duration.ofSeconds(2))
                    .onErrorReturn("DOWN")
                    .block();
            return result != null ? result : "DOWN";
        } catch (Exception e) {
            return "DOWN";
        }
    }
}
