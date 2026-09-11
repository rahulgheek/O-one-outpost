package com.aegis.auth_service.controllers;

import com.aegis.auth_service.dto.AuthResponse;
import com.aegis.auth_service.dto.LoginRequest;
import com.aegis.auth_service.dto.RegisterRequest;
import com.aegis.auth_service.dto.VerifyRequest;
import com.aegis.auth_service.services.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize; // <-- Import this
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.registerUser(request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/create-admin")
    public ResponseEntity<String> createAdmin(@RequestBody RegisterRequest request) {
        authService.registerAdmin(request);
        return ResponseEntity.ok("New Admin Operator successfully registered.");
    }

    @PostMapping("/verify")
    public ResponseEntity<String> verify(@RequestBody VerifyRequest request) {
        return ResponseEntity.ok(authService.verifyOtp(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.loginUser(request));
    }
}