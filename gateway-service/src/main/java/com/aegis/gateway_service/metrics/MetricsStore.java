package com.aegis.gateway_service.metrics;

import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class MetricsStore {
    private final AtomicInteger currentRpsCounter = new AtomicInteger(0);
    private volatile int lastRps = 0;

    public void incrementRequest() {
        currentRpsCounter.incrementAndGet();
    }

    public void snapshotRps() {
        lastRps = currentRpsCounter.getAndSet(0);
    }

    public int getLastRps() {
        return lastRps;
    }
}
