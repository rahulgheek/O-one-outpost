package com.aegis.analytics_service.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Validates that requests originate from the gateway by checking the
 * X-Internal-Token header against a shared secret.
 * When INTERNAL_SECRET is not set (local dev), this filter is a no-op.
 */
@Component
@Order(1)
public class InternalAuthFilter implements Filter {

    @Value("${internal.secret:}")
    private String expectedSecret;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (expectedSecret == null || expectedSecret.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpReq = (HttpServletRequest) request;
        String token = httpReq.getHeader("X-Internal-Token");

        if (expectedSecret.equals(token)) {
            chain.doFilter(request, response);
        } else {
            HttpServletResponse httpResp = (HttpServletResponse) response;
            httpResp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpResp.setContentType("application/json");
            httpResp.getWriter().write("{\"message\":\"Direct access forbidden. Use the gateway.\"}");
        }
    }
}
