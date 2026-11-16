package com.aegis.arena_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Comma-separated origin patterns allowed to open the SockJS handshake.
     * In production set ARENA_ALLOWED_ORIGINS to the frontend origin.
     */
    @Value("${arena.allowed-origins:*}")
    private String allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Broadcasts messages to any frontend client listening to a URL starting with "/topic"
        config.enableSimpleBroker("/topic");

        // Any messages the frontend sends TO the server must start with "/app"
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Browser connects through the gateway: wss://<gateway>/ws-arena
        registry.addEndpoint("/ws-arena")
                .setAllowedOriginPatterns(Arrays.stream(allowedOrigins.split(","))
                        .map(String::trim)
                        .filter(origin -> !origin.isEmpty())
                        .toArray(String[]::new))
                .withSockJS(); // Fallback for stability
    }
}
