package com.aegis.arena_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Broadcasts messages to any frontend client listening to a URL starting with "/topic"
        config.enableSimpleBroker("/topic");

        // Any messages the frontend sends TO the server must start with "/app"
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // This is the actual URL the frontend will connect to (e.g., ws://localhost:808X/ws-arena)
        registry.addEndpoint("/ws-arena")
                .setAllowedOriginPatterns("*") // Allows frontend to connect from any port/domain without CORS errors
                .withSockJS(); // Fallback for stability
    }
}