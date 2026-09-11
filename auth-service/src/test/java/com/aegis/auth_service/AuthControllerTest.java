package com.aegis.auth_service;

import com.aegis.auth_service.controllers.AuthController;
import com.aegis.auth_service.dto.*;
import com.aegis.auth_service.services.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;



import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// @WebMvcTest tells Spring: "Only load the web layer (Controllers), ignore the databases"
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false) // Bypasses Spring Security filters just for this isolated test
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc; // Our "fake Postman" client

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private com.aegis.auth_service.services.JwtService jwtService;

    @Test
    void registerEndpoint_ShouldReturn200Ok() throws Exception {
        // 1. Arrange
        RegisterRequest request = new RegisterRequest("test@aegis.com", "password","User");
        when(authService.registerUser(any(RegisterRequest.class)))
                .thenReturn("Registration initiated! Please check your console for the OTP.");

        // 2. Act & Assert
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))) // Turns our Java Record into JSON
                .andExpect(status().isOk())
                .andExpect(content().string("Registration initiated! Please check your console for the OTP."));
    }

    @Test
    void verifyEndpoint_ShouldReturn200Ok() throws Exception {
        // 1. Arrange
        VerifyRequest request = new VerifyRequest("test@aegis.com", "123456");
        when(authService.verifyOtp(any(VerifyRequest.class)))
                .thenReturn("Email verified successfully! You can now log in.");

        // 2. Act & Assert
        mockMvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("Email verified successfully! You can now log in."));
    }

    @Test
    void loginEndpoint_ShouldReturn200OkAndToken() throws Exception {
        // 1. Arrange
        LoginRequest request = new LoginRequest("test@aegis.com", "password");
        AuthResponse fakeResponse = new AuthResponse("eyJhbGciOiJIUzI1NiIsInR...", "Login successful!");

        when(authService.loginUser(any(LoginRequest.class))).thenReturn(fakeResponse);

        // 2. Act & Assert
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("eyJhbGciOiJIUzI1NiIsInR...")) // Checks the JSON key "token"
                .andExpect(jsonPath("$.message").value("Login successful!"));
    }
}