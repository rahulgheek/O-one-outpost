package com.aegis.auth_service.dto;

public record AuthResponse(
        String token,
        String message,
        String role
) {
    public AuthResponse(String token, String message) {
        this(token, message, null);
    }
}
