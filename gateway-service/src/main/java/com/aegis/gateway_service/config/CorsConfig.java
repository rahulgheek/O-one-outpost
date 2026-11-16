package com.aegis.gateway_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    /**
     * Comma-separated list of allowed origin patterns.
     * Example: https://app.example.com,https://*.vercel.app,http://localhost:*
     */
    @Value("${cors.allowed-origins:https://*.vercel.app,http://localhost:*}")
    private String allowedOrigins;

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();

        corsConfig.setAllowedOriginPatterns(parseOrigins());

        // Allow all standard HTTP methods + OPTIONS for the preflight
        corsConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));

        // Allow all headers (including Authorization for JWTs)
        corsConfig.setAllowedHeaders(List.of("*"));

        // Allow credentials (important if cookies or secure sessions are used)
        corsConfig.setAllowCredentials(true);

        // Cache the preflight response for 1 hour to reduce Gateway load
        corsConfig.setMaxAge(3600L);

        // Apply this configuration to ALL routes mapping through the Gateway
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }

    private List<String> parseOrigins() {
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }
}
