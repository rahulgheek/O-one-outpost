package com.aegis.assessment_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(@Value("${internal.secret:}") String internalSecret) {
        // Fail fast when the sandbox host is unreachable: the gateway's response
        // timeout is 10s, so a matching 10s connect timeout would race it and
        // surface as an opaque gateway error instead of our 409 "try again".
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(30));

        RestTemplate restTemplate = new RestTemplate(factory);

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
