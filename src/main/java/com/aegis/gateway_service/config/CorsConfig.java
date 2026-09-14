package com.aegis.gateway_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();

        // Allow the production Vercel frontend and localhost for dev
        corsConfig.setAllowedOriginPatterns(Arrays.asList(
                "https://ooneoutpost-five.vercel.app",
                "https://*.vercel.app",
                "http://localhost:*"
        ));


        // Allow all standard HTTP methods + OPTIONS for the preflight
        corsConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));

        // Allow all headers (including Authorization for your JWTs)
        corsConfig.setAllowedHeaders(List.of("*"));

        // Allow credentials (important if you ever use cookies or secure sessions)
        corsConfig.setAllowCredentials(true);

        // Cache the preflight response for 1 hour to reduce Gateway load
        corsConfig.setMaxAge(3600L);

        // Apply this configuration to ALL routes mapping through the Gateway
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }
}