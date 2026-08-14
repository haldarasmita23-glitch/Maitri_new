package com.maitri.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Security Configuration for Maitri Backend.
 *
 * ─── WHAT THIS FILE DOES ───────────────────────────────────────────────────
 *
 * This class controls how HTTP requests are secured.
 * Think of it as the "security rulebook" for the entire backend.
 *
 * Currently (Phase 1 — Foundation):
 *   All API requests are OPEN (permitted) because we haven't built
 *   authentication yet. Phase 3 will lock this down by adding
 *   JWT token validation and role-based access rules.
 *
 * ─── KEY CONCEPTS EXPLAINED ────────────────────────────────────────────────
 *
 * CSRF (Cross-Site Request Forgery):
 *   A type of attack where a malicious website tricks a user's browser
 *   into making a request to your API. We DISABLE CSRF because:
 *   - REST APIs use JWT tokens (not browser cookies) for authentication
 *   - CSRF protection is only needed when cookies carry auth state
 *
 * STATELESS Session Management:
 *   Traditional web apps store session data on the server (stateful).
 *   We use STATELESS because:
 *   - Each request carries a JWT token with the user's identity
 *   - No server-side session storage is needed
 *   - This scales better for production (multiple servers don't need to share session state)
 *
 * CORS (Cross-Origin Resource Sharing):
 *   A browser security rule that blocks frontend code from calling a
 *   backend on a DIFFERENT origin (domain + port).
 *   Example problem: Frontend at localhost:5500 calling backend at localhost:8080
 *   → Browser blocks this by default.
 *   → Our CorsConfiguration tells the browser: "localhost:5500 is allowed."
 *
 * ─── PHASE 3 TODO ──────────────────────────────────────────────────────────
 *   - Add JwtAuthenticationFilter before UsernamePasswordAuthenticationFilter
 *   - Replace .anyRequest().permitAll() with role-specific rules
 *   - Add AuthenticationProvider and AuthenticationManager beans
 * ───────────────────────────────────────────────────────────────────────────
 *
 * @Configuration  — Marks this as a Spring configuration class (defines beans)
 * @EnableWebSecurity — Activates Spring Security for web requests
 * @EnableMethodSecurity — Allows @PreAuthorize("hasRole('ADMIN')") on methods (Phase 3+)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Security Filter Chain — The main security rulebook.
     *
     * Spring Security works as a chain of filters.
     * Every HTTP request passes through these filters before reaching a controller.
     * This method defines what that chain looks like.
     *
     * @param http — Spring Security's fluent configuration builder
     * @return The configured filter chain
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Enable CORS using our configuration below
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 2. Disable CSRF (safe for stateless REST APIs with JWT)
                .csrf(AbstractHttpConfigurer::disable)

                // 3. Define which requests require authentication
                //    PHASE 1: All requests are open.
                //    PHASE 3: This will be replaced with specific role rules, e.g.:
                //      .requestMatchers("/api/admin/**").hasRole("ADMIN")
                //      .requestMatchers("/api/users/me").hasAnyRole("USER", "VENDOR")
                //      .requestMatchers(HttpMethod.GET, "/api/vendors").permitAll()
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()  // PHASE 1 ONLY — open access
                )

                // 4. Stateless sessions — no server-side session storage
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                );

        // PHASE 3 ADDITION: Add JWT filter here
        // http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS Configuration — Defines which frontend origins can call our API.
     *
     * During local development:
     *   - http://localhost:5500  → VS Code Live Server
     *   - http://127.0.0.1:5500 → VS Code Live Server (alternate address)
     *   - http://localhost:8080  → Direct backend access / Postman-via-browser
     *
     * In production (Phase 15):
     *   This will be updated to only allow the real production domain.
     *   Hardcoded origins will be replaced with values from environment config.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Allowed origins (where the frontend runs)
        configuration.setAllowedOrigins(Arrays.asList(
                "http://localhost:5500",
                "http://127.0.0.1:5500",
                "http://localhost:8080"
        ));

        // Allowed HTTP methods
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        ));

        // Allow all headers (the Authorization: Bearer <token> header must pass)
        configuration.setAllowedHeaders(List.of("*"));

        // Allow credentials (needed for Authorization header to be sent cross-origin)
        configuration.setAllowCredentials(true);

        // Cache preflight response for 1 hour (reduces OPTIONS requests)
        configuration.setMaxAge(3600L);

        // Apply this CORS config to all /api/** endpoints
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);

        return source;
    }

    /**
     * BCrypt Password Encoder — Securely hashes passwords before storing them.
     *
     * WHY BCRYPT:
     *   - BCrypt is an adaptive hashing algorithm designed for passwords.
     *   - Unlike MD5 or SHA, BCrypt is intentionally slow (computationally expensive).
     *   - Strength 12 = 2^12 = 4096 iterations of hashing.
     *   - This makes brute-force attacks impractical.
     *   - Even if the database is compromised, plaintext passwords cannot be recovered.
     *
     * HOW IT WORKS:
     *   Registration: password "abc123" → BCrypt → "$2a$12$Ge8..."  (stored in DB)
     *   Login: user enters "abc123" → BCrypt compares → match → login succeeds
     *
     * This bean is registered now and will be used in Phase 3 (authentication).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
