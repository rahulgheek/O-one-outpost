package com.aegis.auth_service;

import com.aegis.auth_service.entities.User;
import com.aegis.auth_service.dto.RegisterRequest;
import com.aegis.auth_service.repo.UserRepository;
import com.aegis.auth_service.services.AuthService;
import com.aegis.auth_service.services.OtpService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// This tells JUnit to enable Mockito's fake-object generation
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    // @Mock creates completely fake, empty versions of your dependencies
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private OtpService otpService;

    // @InjectMocks takes the real AuthService, but forces the fake dependencies inside of it!
    @InjectMocks
    private AuthService authService;

    @Test
    void registerUser_ShouldThrowException_WhenEmailAlreadyExists() {
        // 1. Arrange: Create our payload
        RegisterRequest request = new RegisterRequest("duplicate@aegis.com", "password123", "User");

        // Tell the fake repository: "If anyone asks for this email, pretend it already exists and is verified!"
        User existingUser = new User();
        existingUser.setEmailVerified(true);
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(existingUser));

        // 2. Act & Assert: Try to register, and verify it throws a RuntimeException
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            authService.registerUser(request);
        });

        assertEquals("User already exists", exception.getMessage());

        // Verify that because it crashed, we NEVER tried to save a user or generate an OTP
        verify(userRepository, never()).save(any(User.class));
        verify(otpService, never()).saveOtp(anyString(), anyString());
    }

    @Test
    void registerUser_ShouldSuccessfullyRegister_WhenEmailIsNew() {
        // 1. Arrange: Create our payload
        RegisterRequest request = new RegisterRequest("new@aegis.com", "password123", "User");

        // Tell the fake repository: "This email does not exist yet."
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());
        // Tell the fake password encoder: "When you get this password, pretend to hash it."
        when(passwordEncoder.encode(request.password())).thenReturn("hashed-password-123");

        // 2. Act: Run the actual registration method
        String response = authService.registerUser(request);

        // 3. Assert: Verify the success message is correct
        assertTrue(response.contains("User registration request initiated"));

        // The most powerful part of Mockito: Spying!
        // We verify that the repository's .save() method was called EXACTLY 1 time.
        verify(userRepository, times(1)).save(any(User.class));

        // We verify that the OTP service was actually told to save the OTP for this email.
        verify(otpService, times(1)).saveOtp(eq("new@aegis.com"), anyString());
    }
}