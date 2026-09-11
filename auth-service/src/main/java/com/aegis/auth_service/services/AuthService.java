package com.aegis.auth_service.services;

import com.aegis.auth_service.dto.AuthResponse;
import com.aegis.auth_service.dto.LoginRequest;
import com.aegis.auth_service.dto.RegisterRequest;
import com.aegis.auth_service.dto.VerifyRequest;
import com.aegis.auth_service.entities.Role;
import com.aegis.auth_service.entities.User;
import com.aegis.auth_service.repo.UserRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Random;

@Slf4j
@Service
@AllArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final JwtService jwtService;
    private final AuthenticationProvider authenticationProvider;

    public String registerUser(RegisterRequest request) {
        Optional<User> existingUser = userRepository.findByEmail(request.email());
        if (existingUser.isPresent()) {
            if (existingUser.get().isEmailVerified()) {
                throw new RuntimeException("User already exists");
            }
            String otp = generateOTP();
            otpService.saveOtp(request.email(), otp);
            log.info("Resent OTP: {}", otp);
            return "OTP resent. Please verify your email.";
        }

        // --- NEW: DYNAMIC ROLE ASSIGNMENT ---
        Role assignedRole = Role.USER; // Default fallback
        if (request.role() != null && request.role().equalsIgnoreCase("COMPANY")) {
            assignedRole = Role.COMPANY;
        }

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(assignedRole) // <-- Inject the dynamically assigned role
                .emailVerified(false)
                .build();

        userRepository.save(user);

        String otp = generateOTP();
        otpService.saveOtp(request.email(), otp);

        System.out.println("\n========================================");
        System.out.println("DEV MODE: EMAIL MOCKED");
        System.out.println("To: " + request.email());
        System.out.println("OTP: " + otp);
        System.out.println("Role Assigned: " + assignedRole);
        System.out.println("========================================\n");

        return "User registration request initiated";
    }

    public String generateOTP() {
        Random random = new Random();
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }

    public String verifyOtp(VerifyRequest request) {
        String storedOtp = otpService.getOtp(request.email());
        if (storedOtp == null || !storedOtp.equals(request.otp())) {
            throw new RuntimeException("Invalid or Expired OTP");
        }

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new RuntimeException("User does not exist"));

        user.setEmailVerified(true);
        userRepository.save(user);
        otpService.deleteOtp(request.email());

        return "Email verified successfully! You can now log in.";
    }

    public AuthResponse loginUser(LoginRequest request) {
        log.info("Login attempt for email: {}", request.email());

        authenticationProvider.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.email(),
                        request.password()
                )
        );

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.isEmailVerified()) {
            throw new RuntimeException("Email is not verified. Please verify your email first.");
        }

        // The JwtService must embed the user's role into this token for the Gateway to read it!
        String token = jwtService.generateToken(user);
        log.info("Token generated for: {} with role: {}", user.getEmail(), user.getRole());

        return new AuthResponse(token, "Login successful", user.getRole().name());
    }

    public String registerAdmin(RegisterRequest request) {
        Optional<User> existingUser = userRepository.findByEmail(request.email());
        if (existingUser.isPresent()) {
            throw new RuntimeException("Admin user already exists");
        }

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.ADMIN)
                .emailVerified(true)
                .build();

        userRepository.save(user);

        log.info("New Admin Operator registered: {}", request.email());
        return "Admin registration complete.";
    }
}