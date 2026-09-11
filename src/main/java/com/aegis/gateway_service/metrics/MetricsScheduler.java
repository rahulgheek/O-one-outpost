package com.aegis.gateway_service.metrics;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MetricsScheduler {
    private final MetricsStore metricsStore;

    @Scheduled(fixedRate = 1000)
    public void captureRps() {
        metricsStore.snapshotRps();
    }
}
