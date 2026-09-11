package com.aegis.arena_service.config;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class WebSocketMetrics {
    private final AtomicInteger activeConnections = new AtomicInteger(0);

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        activeConnections.incrementAndGet();
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        activeConnections.decrementAndGet();
    }

    public int getActiveConnections() {
        return Math.max(0, activeConnections.get());
    }
}
