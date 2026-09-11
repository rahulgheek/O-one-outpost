package com.aegis.auth_service.config;

import com.aegis.auth_service.services.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;

        // 1. Check if the token is missing or doesn't start with "Bearer "
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Extract the token (skip the "Bearer " part)
        jwt = authHeader.substring(7);

        try {
            // 3. Extract the email from the token
            userEmail = jwtService.extractUsername(jwt);

            // 4. If email is present and user is not already authenticated in this exact thread
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // Fetch the user from your database via the UserDetailsService we built
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

                // 5. Validate the token mathematically
                if (jwtService.isTokenValid(jwt, userDetails)) {

                    // Create the VIP Pass (Authentication Token)
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities() // This pulls the ROLE_ADMIN or ROLE_COMPANY
                    );

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // 6. Lock the VIP Pass into the Security Context!
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // If the token is expired, forged, or malformed, we just log it.
            // We don't crash; we just let the filter chain continue without authenticating them,
            // which results in a clean 403 Forbidden.
            System.out.println("JWT Filter Rejected Token: " + e.getMessage());
        }

        // 7. Pass to the next filter in the chain
        filterChain.doFilter(request, response);
    }
}