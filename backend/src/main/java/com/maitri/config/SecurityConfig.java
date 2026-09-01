package com.maitri.config;

import com.maitri.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Security Configuration for Maitri Backend — Phase 3A (JWT Authentication).
 *
 * ─── WHAT THIS FILE DOES ───────────────────────────────────────────────────
 *
 * This class controls how HTTP requests are secured.
 * It is the "security rulebook" for the entire backend.
 *
 * Phase 3A adds:
 *   - JWT authentication filter (JwtAuthenticationFilter) on every request
 *   - Explicit public vs protected endpoint rules
 *   - DaoAuthenticationProvider backed by our UserDetailsServiceImpl
 *   - AuthenticationManager bean for use in AuthService
 *
 * ─── ENDPOINT ACCESS RULES ───────────────────────────────────────────────
 *
 *   PUBLIC (no JWT required):
 *     GET  /api/health          — Liveness check
 *     POST /api/auth/register   — Public registration
 *     POST /api/auth/login      — Login (returns JWT)
 *
 *   PROTECTED (valid JWT required):
 *     GET  /api/auth/me         — Current user profile
 *     All other /api/** paths   — Protected by default
 *
 * ─── KEY CONCEPTS ────────────────────────────────────────────────────────
 *
 *   CSRF: Disabled — REST APIs with JWT don't use browser cookies for auth.
 *
 *   STATELESS: No server-side sessions. Each request carries a JWT.
 *
 *   JWT FILTER: Runs before UsernamePasswordAuthenticationFilter.
 *     Extracts the Bearer token, validates it, and sets authentication
 *     in the SecurityContext if valid.
 *
 *   DaoAuthenticationProvider: Used by Spring Security's login mechanism.
 *     Loads the user by email, verifies the BCrypt password.
 *
 * @Configuration      — Marks this as a Spring configuration class
 * @EnableWebSecurity  — Activates Spring Security for web requests
 * @EnableMethodSecurity — Enables @PreAuthorize on controller methods
 * @RequiredArgsConstructor — Constructor injection for all final fields
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsService userDetailsService;

    /**
     * Security Filter Chain — The main security rulebook.
     *
     * Defines:
     *   - Which endpoints are public
     *   - Which endpoints require authentication
     *   - That sessions are stateless
     *   - That the JWT filter runs before standard auth filters
     *
     * @param http Spring Security's fluent configuration builder
     * @return The configured filter chain
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // ── CORS ─────────────────────────────────────────────────────────
                // Apply our CORS rules to allow the frontend to call the API
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // ── CSRF ─────────────────────────────────────────────────────────
                // Disabled: JWT in Authorization header is not vulnerable to CSRF.
                // CSRF is only a risk when the browser auto-sends cookies with requests.
                .csrf(AbstractHttpConfigurer::disable)

                // ── Authorization Rules ───────────────────────────────────────────
                // Define exactly which endpoints are public and which require a JWT.
                .authorizeHttpRequests(auth -> auth

                        // Public endpoints — no JWT required
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/nlp/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categories").permitAll()

                        // ── Phase 5: Vendor browsing is public ──────────────
                        // NOTE: order matters (first match wins). "/api/vendors/me"
                        // must be declared BEFORE the wildcard "/api/vendors/*",
                        // otherwise it would be exposed publicly.
                        .requestMatchers(HttpMethod.GET, "/api/vendors/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/vendors").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/vendors/*").permitAll()

                        // ── Phase 7: Review viewing is public ───────────────
                        // NOTE: order matters. "/api/reviews/my" must be declared
                        // BEFORE the wildcard "/api/reviews/vendor/*", otherwise
                        // it would be exposed publicly.
                        .requestMatchers(HttpMethod.GET, "/api/reviews/my").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/reviews/vendor/**").permitAll()

                        // Public vendor ratings summary
                        .requestMatchers(HttpMethod.GET, "/api/reviews/vendor/*/summary").permitAll()

                        // Swagger/OpenAPI (development only)
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()

                        // Actuator health (if enabled)
                        .requestMatchers("/actuator/health").permitAll()

                        // WebSocket (if used later)
                        .requestMatchers("/ws/**").permitAll()

                        // All other requests require authentication (valid JWT)
                        .anyRequest().authenticated()
                )

                // ── Session Management ────────────────────────────────────────────
                // STATELESS: No server-side sessions. Each request is fully self-contained.
                // Spring Security will NEVER create an HttpSession for authentication.
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // ── Exception Handling ────────────────────────────────────────────────
                // Return 401 (not 403) when an unauthenticated request hits a protected
                // endpoint. 401 = "you need to authenticate", 403 = "authenticated but
                // not authorized". Spring Security defaults to 403 without this.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint())
                )

                // ── Authentication Provider ───────────────────────────────────────
                // Wire in our DaoAuthenticationProvider (uses our UserDetailsService
                // and BCryptPasswordEncoder for credential verification)
                .authenticationProvider(authenticationProvider())

                // ── JWT Filter ────────────────────────────────────────────────────
                // Inject our JWT filter BEFORE Spring's default username/password filter.
                // This means: for every request, we first try to authenticate via JWT.
                // If no valid JWT is found, the request proceeds as anonymous.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @org.springframework.beans.factory.annotation.Value("${cors.allowed.origins:${CORS_ALLOWED_ORIGINS:}}")
    private String corsAllowedOrigins;

    /**
     * CORS Configuration — Defines which frontend origins can call our API.
     *
     * During local development:
     *   - http://localhost:*     → Local dev ports (3000, 5000, 5500, 8080)
     *   - http://127.0.0.1:*     → Local dev loopback
     *   - http://192.168.*:*     → Local LAN / network IP (e.g. http://192.168.1.67:3000)
     *   - http://10.*:*          → Private network IP
     *   - http://172.16.*:* to http://172.31.*:* → Private Docker / LAN IP ranges
     *
     * In production / cloud:
     *   - https://*.vercel.app   → All Vercel deployments (preview and production)
     *   - https://*.onrender.com → Render hosted apps
     *   - https://*.netlify.app  → Netlify hosted apps
     *   - Configured via CORS_ALLOWED_ORIGINS environment variable or cors.allowed.origins property.
     *     Comma/semicolon-separated list of allowed origins.
     *     Example: https://maitri.in,https://www.maitri.in,http://192.168.1.67:3000
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Allowed origin patterns (where the frontend runs)
        // Supports all Vercel deployments (*.vercel.app), Render (*.onrender.com),
        // local dev ports (localhost, 127.0.0.1), and local network IP addresses (192.168.*, 10.*, 172.*).
        List<String> allowedOriginPatterns = new ArrayList<>(Arrays.asList(
                "https://*.vercel.app",
                "https://*.onrender.com",
                "https://*.netlify.app",
                "http://localhost:*",
                "http://127.0.0.1:*",
                "http://192.168.*:*",
                "http://10.*:*",
                "http://172.16.*:*",
                "http://172.17.*:*",
                "http://172.18.*:*",
                "http://172.19.*:*",
                "http://172.20.*:*",
                "http://172.21.*:*",
                "http://172.22.*:*",
                "http://172.23.*:*",
                "http://172.24.*:*",
                "http://172.25.*:*",
                "http://172.26.*:*",
                "http://172.27.*:*",
                "http://172.28.*:*",
                "http://172.29.*:*",
                "http://172.30.*:*",
                "http://172.31.*:*"
        ));

        // Read custom origins from property / environment variable
        String envOrigins = (corsAllowedOrigins != null && !corsAllowedOrigins.isBlank())
                ? corsAllowedOrigins
                : System.getenv("CORS_ALLOWED_ORIGINS");

        if (envOrigins != null && !envOrigins.isBlank()) {
            for (String raw : envOrigins.split("[,;]+")) {
                String origin = raw.trim().replaceAll("/+$", "");
                if (!origin.isBlank() && !allowedOriginPatterns.contains(origin)) {
                    allowedOriginPatterns.add(origin);
                }
            }
        }

        configuration.setAllowedOriginPatterns(allowedOriginPatterns);

        // Allowed HTTP methods
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        ));

        // Allow all headers (Authorization: Bearer <token>, Content-Type, etc.)
        configuration.setAllowedHeaders(List.of("*"));

        // Exposed headers that the client JavaScript can read
        configuration.setExposedHeaders(Arrays.asList(
                "Authorization", "Content-Type", "X-Total-Count"
        ));

        // Allow credentials (needed for Authorization header cross-origin)
        configuration.setAllowCredentials(true);

        // Cache preflight response for 1 hour (reduces OPTIONS requests)
        configuration.setMaxAge(3600L);

        // Apply this CORS config to all endpoints
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    /**
     * BCrypt Password Encoder — Securely hashes passwords before storing them.
     *
     * Strength 12 = 2^12 = 4096 iterations. Intentionally slow to resist brute-force.
     * Even if the database is compromised, plaintext passwords cannot be recovered.
     *
     * Registration: "password123" → BCrypt → "$2a$12$Ge8..."  (stored in DB)
     * Login:        "password123" → BCrypt.matches() → match → login succeeds
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Authentication Provider — Wires our UserDetailsService and PasswordEncoder
     * into Spring Security's authentication mechanism.
     *
     * DaoAuthenticationProvider:
     *   - Loads the user from MongoDB by email (via UserDetailsServiceImpl)
     *   - Verifies the submitted password against the stored BCrypt hash
     *   - Throws BadCredentialsException if the password is wrong
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Authentication Manager — Used programmatically to trigger authentication.
     *
     * AuthService uses this to authenticate user credentials during login.
     * Spring's AuthenticationConfiguration creates the manager using the
     * authenticationProvider() bean defined above.
     *
     * @param config Spring's authentication configuration
     * @return The configured AuthenticationManager
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Authentication Entry Point — Returns HTTP 401 when an unauthenticated request
     * reaches a protected endpoint.
     *
     * Without this, Spring Security defaults to 403 Forbidden, which is semantically
     * incorrect for unauthenticated access:
     *   401 = "You need to authenticate (provide credentials)"
     *   403 = "You are authenticated, but not authorized for this resource"
     *
     * This entry point writes a minimal JSON response consistent with ApiResponse format.
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"success\":false,\"message\":\"Authentication required. Please log in.\"}"
            );
        };
    }
}
