package com.aegis.arena_service.controller;

import com.aegis.arena_service.config.WebSocketMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/arena")
@RequiredArgsConstructor
public class MetricsController {
    private final WebSocketMetrics webSocketMetrics;

    @GetMapping("/metrics")
    public Map<String, Integer> getMetrics() {
        return Map.of("activeWebSockets", webSocketMetrics.getActiveConnections());
    }
}
