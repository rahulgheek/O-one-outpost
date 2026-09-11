package com.aegis.gateway_service.controller;

import com.aegis.gateway_service.metrics.MetricsStore;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/gateway")
@RequiredArgsConstructor
public class MetricsController {
    private final MetricsStore metricsStore;

    @GetMapping("/metrics")
    public Map<String, Integer> getMetrics() {
        return Map.of("rps", metricsStore.getLastRps());
    }
}
