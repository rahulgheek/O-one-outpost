package com.aegis.gateway_service.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global filter that injects the internal shared secret header into every
 * request forwarded by the gateway to downstream services.
 * Downstream services validate this header to ensure requests originate
 * from the gateway and not from an external source.
 */
@Component
public class InternalTokenInjectionFilter implements GlobalFilter, Ordered {

    @Value("${internal.secret:}")
    private String internalSecret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (internalSecret == null || internalSecret.isBlank()) {
            // No secret configured (local dev) — skip injection
            return chain.filter(exchange);
        }

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(r -> r.header("X-Internal-Token", internalSecret))
                .build();

        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        // Run before other filters so the header is present for all routes
        return -1;
    }
}
