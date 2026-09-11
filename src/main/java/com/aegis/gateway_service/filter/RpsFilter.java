package com.aegis.gateway_service.filter;

import com.aegis.gateway_service.metrics.MetricsStore;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class RpsFilter implements GlobalFilter, Ordered {
    private final MetricsStore metricsStore;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().toString();
        // Exclude metric collection endpoints from counting towards true user traffic
        if (!path.contains("/api/gateway/metrics") && !path.contains("/api/analytics/dashboard")) {
            metricsStore.incrementRequest();
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
