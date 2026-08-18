package com.maitri.controller;

import com.maitri.dto.ApiResponse;
import com.maitri.dto.auth.AuthResponse;
import com.maitri.dto.auth.LoginRequest;
import com.maitri.dto.auth.RegisterRequest;
import com.maitri.dto.auth.UserResponse;
import com.maitri.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication Controller — HTTP entry points for auth operations.
 *
 * ─── ENDPOINTS ────────────────────────────────────────────────────────────────
 *
 *   POST /api/auth/register    (PUBLIC)
 *   POST /api/auth/login       (PUBLIC)
 *   GET  /api/auth/me          (PROTECTED — requires valid JWT)
 *
 * ─── DESIGN PRINCIPLES ───────────────────────────────────────────────────────
 *   This controller is intentionally thin:
 *   - It validates request data (via @Valid)
 *   - It calls AuthService for all business logic
 *   - It wraps results in ApiResponse<T> for consistent response format
 *   - It does NOT contain any authentication logic itself
 *
 * ─── SECURITY ────────────────────────────────────────────────────────────────
 *   - Public endpoints are declared open in SecurityConfig (no auth required)
 *   - GET /api/auth/me is protected — JwtAuthenticationFilter must set
 *     an authentication in SecurityContextHolder before this method runs.
 *     If the JWT is missing/invalid, Spring Security returns 401 before
 *     this controller method is ever invoked.
 *
 * @RestController: All methods return JSON (no view rendering).
 * @RequestMapping("/api/auth"): All endpoints begin with /api/auth.
 * @RequiredArgsConstructor: Generates constructor injection for authService.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Register a new user account.
     *
     * POST /api/auth/register
     * Access: Public (no authentication required)
     *
     * Request Body (JSON):
     * {
     *   "name": "Ramesh Kumar",
     *   "email": "ramesh@example.com",
     *   "password": "password123",
     *   "role": "USER"           (optional — defaults to USER; VENDOR also allowed)
     * }
     *
     * Success Response (HTTP 201 Created):
     * {
     *   "success": true,
     *   "message": "Registration successful.",
     *   "data": {
     *     "token": "eyJhbGciOiJIUzI1NiJ9...",
     *     "user": { "id": "...", "name": "Ramesh Kumar", "email": "...", "role": "USER" }
     *   }
     * }
     *
     * Error Responses:
     *   400 — Validation failed (missing name, invalid email, password too short)
     *   409 — Email already registered
     *   403 — Attempted to register as ADMIN/SUPER_ADMIN
     *
     * @param request Validated registration data
     * @return 201 Created with JWT and user info on success
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {

        AuthResponse authResponse = authService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful.", authResponse));
    }

    /**
     * Authenticate a user and return a JWT.
     *
     * POST /api/auth/login
     * Access: Public (no authentication required)
     *
     * Request Body (JSON):
     * {
     *   "email": "ramesh@example.com",
     *   "password": "password123"
     * }
     *
     * Success Response (HTTP 200 OK):
     * {
     *   "success": true,
     *   "message": "Login successful.",
     *   "data": {
     *     "token": "eyJhbGciOiJIUzI1NiJ9...",
     *     "user": { "id": "...", "name": "Ramesh Kumar", "email": "...", "role": "USER" }
     *   }
     * }
     *
     * Error Responses:
     *   400 — Validation failed (blank email, invalid format, blank password)
     *   401 — Invalid email or password (generic — never reveals which is wrong)
     *
     * @param request Validated login credentials
     * @return 200 OK with JWT and user info on success
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        AuthResponse authResponse = authService.login(request);

        return ResponseEntity.ok(ApiResponse.success("Login successful.", authResponse));
    }

    /**
     * Get the currently authenticated user's profile.
     *
     * GET /api/auth/me
     * Access: PROTECTED — requires "Authorization: Bearer <token>" header
     *
     * If no valid JWT is provided, Spring Security returns 401 BEFORE
     * this method is called.
     *
     * Success Response (HTTP 200 OK):
     * {
     *   "success": true,
     *   "message": "User profile retrieved.",
     *   "data": {
     *     "id": "...",
     *     "name": "Ramesh Kumar",
     *     "email": "ramesh@example.com",
     *     "role": "USER",
     *     "active": true,
     *     "createdAt": "2026-08-14T18:00:00"
     *   }
     * }
     *
     * @AuthenticationPrincipal UserDetails userDetails:
     *   Spring Security automatically injects the currently authenticated user's
     *   UserDetails object (set by JwtAuthenticationFilter in SecurityContext).
     *   userDetails.getUsername() returns the email (our "username").
     *
     * @param userDetails The authenticated user's details (injected by Spring Security)
     * @return 200 OK with safe user profile data (no password)
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails) {

        UserResponse userResponse = authService.getCurrentUser(userDetails.getUsername());

        return ResponseEntity.ok(ApiResponse.success("User profile retrieved.", userResponse));
    }
}
