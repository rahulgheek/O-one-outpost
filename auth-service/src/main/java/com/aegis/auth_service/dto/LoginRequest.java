package com.aegis.auth_service.dto;

public record LoginRequest(
        String email,
        String password
) {
}
