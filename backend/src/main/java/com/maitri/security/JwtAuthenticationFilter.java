package com.maitri.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT Authentication Filter — Validates JWT tokens on every incoming HTTP request.
 *
 * ─── WHERE THIS FITS IN THE REQUEST LIFECYCLE ────────────────────────────────
 *
 *   HTTP Request
 *        ↓
 *   [JwtAuthenticationFilter]  ← THIS CLASS
 *        ↓ (if valid JWT)
 *   SecurityContextHolder.setAuthentication(...)
 *        ↓
 *   [Other Spring Security Filters]
 *        ↓
 *   @RestController method
 *
 * ─── WHAT THIS FILTER DOES ───────────────────────────────────────────────────
 *   For EVERY request:
 *   1. Check for "Authorization: Bearer <token>" header
 *   2. If missing or malformed → skip (let Spring Security handle as anonymous)
 *   3. If present → extract email from JWT via JwtService
 *   4. If email found and no authentication already set → load user from DB
 *   5. If token is valid → set authentication in SecurityContextHolder
 *   6. Always call filterChain.doFilter() to pass the request forward
 *
 * ─── WHY OncePerRequestFilter? ───────────────────────────────────────────────
 *   OncePerRequestFilter guarantees this filter runs exactly once per HTTP request,
 *   even when requests are forwarded internally within Spring.
 *
 * ─── SECURITY NOTES ──────────────────────────────────────────────────────────
 *   - We do NOT authenticate if SecurityContext already has an authentication
 *     (avoids processing duplicate auth on the same request)
 *   - Expired/invalid tokens are caught and silently skipped —
 *     the request will proceed as anonymous, and protected endpoints will
 *     return 401 via Spring Security's AuthenticationEntryPoint
 *   - We NEVER log the token value itself
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * @RequiredArgsConstructor: Generates a constructor injecting all final fields.
 * @Slf4j: Provides a `log` field for SLF4J logging (via Lombok).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    /**
     * Core filter logic — executed for every HTTP request.
     *
     * @param request     The incoming HTTP request
     * @param response    The outgoing HTTP response
     * @param filterChain The remaining filter chain to call after this filter
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // ── Step 1: Extract the Authorization header ──────────────────────────
        final String authHeader = request.getHeader("Authorization");

        // If there's no Authorization header, or it doesn't start with "Bearer ",
        // skip JWT processing. The request will proceed as unauthenticated.
        // Public endpoints will succeed; protected endpoints will return 401.
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // ── Step 2: Extract the token (strip "Bearer " prefix) ────────────────
        final String jwt = authHeader.substring(7);

        // ── Step 3: Parse the email from the token ────────────────────────────
        final String userEmail;
        try {
            userEmail = jwtService.extractEmail(jwt);
        } catch (ExpiredJwtException e) {
            log.debug("JWT token has expired for request to: {}", request.getRequestURI());
            // Let the request proceed as unauthenticated — Spring Security will handle it
            filterChain.doFilter(request, response);
            return;
        } catch (JwtException e) {
            log.debug("Invalid JWT token for request to: {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        // ── Step 4: Authenticate if not already authenticated ─────────────────
        // SecurityContextHolder.getContext().getAuthentication() is null when
        // no authentication has been set for this request yet.
        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // Load user from MongoDB via UserDetailsService
            UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

            // ── Step 5: Validate token against the loaded user ─────────────────
            if (jwtService.isTokenValid(jwt, userDetails)) {

                // Create a Spring Security authentication token
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,          // principal (the UserDetails object)
                                null,                 // credentials (null — JWT auth uses no password here)
                                userDetails.getAuthorities()  // granted authorities (roles)
                        );

                // Attach request details (IP, session info) for auditing
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // ── Step 6: Set authentication in the SecurityContext ──────────
                // This is what tells Spring Security "this user is authenticated".
                // From this point, @PreAuthorize, hasRole(), etc. will work.
                SecurityContextHolder.getContext().setAuthentication(authToken);

                log.debug("JWT authentication successful for user: {} on path: {}",
                        userEmail, request.getRequestURI());
            }
        }

        // ── Step 7: Continue the filter chain ─────────────────────────────────
        filterChain.doFilter(request, response);
    }
}
