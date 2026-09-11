package com.aegis.auth_service.dto;

public record VerifyRequest(
        String email,String otp
) {
}
