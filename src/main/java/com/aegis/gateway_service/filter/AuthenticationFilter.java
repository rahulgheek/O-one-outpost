package com.aegis.gateway_service.filter;

import com.aegis.gateway_service.util.JwtUtil;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private final JwtUtil jwtUtil;

    public AuthenticationFilter(JwtUtil jwtUtil) {
        super(Config.class);
        this.jwtUtil = jwtUtil;
    }

    public static class Config {
        // Required empty config class
    }

    @Override
    public GatewayFilter apply(Config config) {
        return ((exchange, chain) -> {

            String path = exchange.getRequest().getURI().getPath();

            // ✅ Skip auth for WebSocket handshake
            if (path.contains("/ws-arena")) {
                return chain.filter(exchange);
            }

            // Grab the Authorization header
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            // 1. Check if the header is completely missing or malformed
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                System.out.println("[🛡️ AEGIS] Blocked request: Missing or Invalid Authorization Header");
                ServerHttpResponse response = exchange.getResponse();
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return response.setComplete();
            }

            // Strip the "Bearer " prefix to isolate the JWT
            String token = authHeader.substring(7);

            try {
                // 2. Validate the token mathematically
                jwtUtil.validateToken(token);

                // 3. Extract User ID and Role
                String userId = jwtUtil.extractUserId(token);
                String userRole = jwtUtil.extractRole(token);


                // If the path is a COMPANY endpoint, strictly enforce the COMPANY role (or ADMIN)
                if (path.startsWith("/api/assessment/company") && !("COMPANY".equals(userRole) || "ADMIN".equals(userRole))) {
                    System.out.println("[🛡️ AEGIS-RBAC] Blocked: User " + userId + " attempted to access a Company route with role: " + userRole);
                    ServerHttpResponse response = exchange.getResponse();
                    response.setStatusCode(HttpStatus.FORBIDDEN);
                    return response.setComplete();
                }

                // If the path is an ADMIN endpoint, strictly enforce the ADMIN role
                if (path.contains("/admin/") && !"ADMIN".equals(userRole)) {
                    System.out.println("[🛡️ AEGIS-RBAC] Blocked: User " + userId + " attempted to access an Admin route.");
                    ServerHttpResponse response = exchange.getResponse();
                    response.setStatusCode(HttpStatus.FORBIDDEN);
                    return response.setComplete();
                }

                // All candidate routes belong to players. This includes
                // /api/candidate/join and the workspace endpoints. Only the
                // directory was previously exempted, which let COMPANY/ADMIN
                // tokens reach candidate flows.
                boolean isCandidateZone = path.equals("/api/candidate")
                        || path.startsWith("/api/candidate/");

                if (isCandidateZone && !"USER".equals(userRole)) {
                    System.out.println("[🛡️ AEGIS-RBAC] Blocked: Role " + userRole + " attempted to access a candidate route.");
                    ServerHttpResponse response = exchange.getResponse();
                    response.setStatusCode(HttpStatus.FORBIDDEN);
                    return response.setComplete();
                }

                // Competitive matches are execution zones too.
                boolean isExecutionZone = path.startsWith("/api/match");

                if (isExecutionZone && !"USER".equals(userRole)) {
                    System.out.println("[🛡️ AEGIS-RBAC] Blocked: Role " + userRole + " attempted to enter a player execution zone.");
                    ServerHttpResponse response = exchange.getResponse();
                    response.setStatusCode(HttpStatus.FORBIDDEN);
                    return response.setComplete();
                }
                // ==========================================

                // Player profiles should strictly only be accessible by real USER (player) accounts.
                // Neither Companies nor Admins are players and must NEVER have player profiles.
                if (path.startsWith("/api/profiles") && !"USER".equals(userRole) && !path.contains("/leaderboard")) {
                    System.out.println("[🛡️ AEGIS-RBAC] Blocked: Non-player role " + userRole + " attempted to access player profile.");
                    ServerHttpResponse response = exchange.getResponse();
                    response.setStatusCode(HttpStatus.FORBIDDEN);
                    return response.setComplete();
                }

                // ==========================================

                // 4. Mutate the request to pass it downstream
                exchange = exchange.mutate()
                        .request(r -> r.header("loggedInUser", userId)
                                .header("X-User-Role", userRole))
                        .build();

            } catch (Exception e) {
                // Catch expired or forged tokens gracefully
                System.out.println("[🛡️ AEGIS] Blocked request: Invalid or Expired Token -> " + e.getMessage());
                ServerHttpResponse response = exchange.getResponse();
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return response.setComplete();
            }

            // 5. If everything is perfect, forward the request to the target service
            return chain.filter(exchange);
        });
    }
}