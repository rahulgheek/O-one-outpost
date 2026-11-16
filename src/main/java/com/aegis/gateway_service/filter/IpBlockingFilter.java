package com.aegis.gateway_service.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
public class IpBlockingFilter extends AbstractGatewayFilterFactory<IpBlockingFilter.Config> {

    private final ReactiveStringRedisTemplate redisTemplate;

    public IpBlockingFilter(ReactiveStringRedisTemplate redisTemplate) {
        super(Config.class);
        this.redisTemplate = redisTemplate;
    }

    public static class Config {

    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {

            // Resolve the real client IP. Behind Botkeep/Caddy the TCP peer is the
            // edge proxy, so use the forwarded headers when present.
            String ip = resolveClientIp(exchange);

            String jailKey = "aegis:jail:ip:" + ip;
            String countKey = "aegis:count:ip:" + ip;

            // 1. Check if the IP is already in Jail
            return redisTemplate.hasKey(jailKey).flatMap(isJailed -> {
                if (Boolean.TRUE.equals(isJailed)) {
                    System.out.println("[🛑 AEGIS] Dropped request from JAILED IP: " + ip);
                    ServerHttpResponse response = exchange.getResponse();
                    response.setStatusCode(HttpStatus.FORBIDDEN); // 403 Forbidden
                    return response.setComplete();
                }

                // 2. If not jailed, increment their request count
                return redisTemplate.opsForValue().increment(countKey).flatMap(count -> {
                    if (count == 1) {
                        // First strike: Start the 5-minute timer
                        return redisTemplate.expire(countKey, Duration.ofMinutes(5))
                                .then(chain.filter(exchange));
                    } else if (count > 1000) {
                        // Limit exceeded! Throw them in Jail for 24 hours
                        System.out.println("[🚨 AEGIS] THREAT DETECTED! Jailing IP: " + ip);
                        return redisTemplate.opsForValue().set(jailKey, "BLOCKED", Duration.ofHours(24))
                                .then(Mono.defer(() -> {
                                    ServerHttpResponse response = exchange.getResponse();
                                    response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                                    return response.setComplete();
                                }));
                    }

                    // Normal request, let it pass
                    return chain.filter(exchange);
                });
            });
        };
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        String realIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // First entry is the original client; subsequent entries are proxies.
            return forwardedFor.split(",")[0].trim();
        }

        return exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "UNKNOWN";
    }
}
