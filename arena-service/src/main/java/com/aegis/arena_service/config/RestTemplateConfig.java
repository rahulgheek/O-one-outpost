package com.aegis.arena_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(@Value("${internal.secret:}") String internalSecret) {
        RestTemplate restTemplate = new RestTemplate();

        // Every outbound call (currently the sandbox) must carry the internal
        // shared secret. Downstream services reject requests without it.
        if (internalSecret != null && !internalSecret.isBlank()) {
            restTemplate.getInterceptors().add((request, body, execution) -> {
                request.getHeaders().set("X-Internal-Token", internalSecret);
                return execution.execute(request, body);
            });
        }

        return restTemplate;
    }
}
