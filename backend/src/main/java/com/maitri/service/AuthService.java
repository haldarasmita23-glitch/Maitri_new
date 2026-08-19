package com.maitri.service;

import com.maitri.dto.auth.AuthResponse;
import com.maitri.dto.auth.LoginRequest;
import com.maitri.dto.auth.RegisterRequest;
import com.maitri.dto.auth.UserResponse;
import com.maitri.exception.DuplicateEmailException;
import com.maitri.exception.InvalidCredentialsException;
import com.maitri.model.Role;
import com.maitri.model.User;
import com.maitri.repository.UserRepository;
import com.maitri.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Authentication Service — Core business logic for user registration and login.
 *
 * ─── RESPONSIBILITIES ────────────────────────────────────────────────────────
 *   register() — Validates, creates, and persists a new user account
 *   login()    — Validates credentials and issues a JWT
 *   getCurrentUser() — Returns safe user data for the /me endpoint
 *
 * ─── ROLE ASSIGNMENT SECURITY ────────────────────────────────────────────────
 *   Public registration (via POST /api/auth/register) can only produce:
 *     - Role.USER  (default if no role provided)
 *     - Role.VENDOR (explicitly requested)
 *   Attempting to register as ADMIN or SUPER_ADMIN is rejected with 403.
 *   This prevents privilege escalation through the public registration API.
 *
 * ─── PASSWORD SECURITY ───────────────────────────────────────────────────────
 *   - Passwords are ALWAYS hashed with BCrypt before storage
 *   - Plain text passwords are NEVER logged
 *   - Plain text passwords are NEVER stored in any field
 *   - The `passwordEncoder.matches()` method is used for comparison during login
 *
 * ─── LAYER SEPARATION ────────────────────────────────────────────────────────
 *   This service never directly touches HTTP (no HttpServletRequest/Response).
 *   It works with plain Java objects (DTOs, domain models).
 *   The controller handles HTTP concerns; this service handles business logic.
 *
 * @RequiredArgsConstructor: Generates constructor injection for all final fields.
 * @Slf4j: Provides a `log` field for SLF4J logging.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    // ─── Registration ─────────────────────────────────────────────────────────

    /**
     * Registers a new user account.
     *
     * Steps:
     *   1. Check for duplicate email
     *   2. Validate requested role (prevent ADMIN/SUPER_ADMIN via public registration)
     *   3. Assign default role if none provided
     *   4. Hash the password with BCrypt
     *   5. Save the user to MongoDB
     *   6. Generate a JWT for the new user
     *   7. Return AuthResponse with the JWT and safe user info
     *
     * @param request Registration data (name, email, password, optional role)
     * @return AuthResponse containing JWT token and UserResponse
     * @throws DuplicateEmailException if the email is already registered
     * @throws AccessDeniedException   if the requested role is ADMIN or SUPER_ADMIN
     */
    public AuthResponse register(RegisterRequest request) {

        // ── Step 1: Check for duplicate email ─────────────────────────────────
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException("An account with this email already exists.");
        }

        // ── Step 2: Validate role — prevent privilege escalation ───────────────
        Role requestedRole = request.getRole();

        if (requestedRole == Role.ADMIN || requestedRole == Role.SUPER_ADMIN) {
            // Log the attempt server-side (without personal data like email)
            log.warn("Attempt to register with elevated role '{}' via public endpoint — rejected.",
                    requestedRole);
            throw new AccessDeniedException(
                    "You cannot register with the role '" + requestedRole + "'. " +
                    "Please contact an administrator."
            );
        }

        // ── Step 3: Assign default role ────────────────────────────────────────
        Role assignedRole = (requestedRole != null) ? requestedRole : Role.USER;

        // ── Step 4: Hash the password ──────────────────────────────────────────
        // NEVER log the raw password. NEVER store it in any variable beyond this line.
        String hashedPassword = passwordEncoder.encode(request.getPassword());

        // ── Step 5: Build and save the User document ───────────────────────────
        LocalDateTime now = LocalDateTime.now();
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail().toLowerCase().trim())  // normalize email
                .password(hashedPassword)
                .role(assignedRole)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        User savedUser = userRepository.save(user);
        log.info("New user registered: id={}, role={}", savedUser.getId(), savedUser.getRole());

        // ── Step 6: Generate JWT ───────────────────────────────────────────────
        UserDetails userDetails = userDetailsService.loadUserByUsername(savedUser.getEmail());
        String token = jwtService.generateToken(userDetails);

        // ── Step 7: Build and return AuthResponse ──────────────────────────────
        return AuthResponse.builder()
                .token(token)
                .user(toUserResponse(savedUser))
                .build();
    }

    // ─── Login ────────────────────────────────────────────────────────────────

    /**
     * Authenticates a user and issues a JWT.
     *
     * Steps:
     *   1. Find user by email
     *   2. Verify password against stored BCrypt hash
     *   3. Check account is active
     *   4. Generate and return JWT
     *
     * On any failure (user not found, wrong password, inactive account),
     * we throw InvalidCredentialsException with a GENERIC message.
     * This prevents user enumeration — attackers cannot determine which
     * emails are registered vs which passwords are wrong.
     *
     * @param request Login credentials (email, password)
     * @return AuthResponse containing JWT token and UserResponse
     * @throws InvalidCredentialsException if email/password/account is invalid
     */
    public AuthResponse login(LoginRequest request) {

        // ── Step 1: Find user by email ─────────────────────────────────────────
        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password."));

        // ── Step 2: Verify password ────────────────────────────────────────────
        // passwordEncoder.matches() compares raw input against the BCrypt hash.
        // Returns false if the password is wrong — we throw the same generic exception.
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.debug("Failed login attempt for user id: {}", user.getId());
            throw new InvalidCredentialsException("Invalid email or password.");
        }

        // ── Step 3: Check account is active ───────────────────────────────────
        if (!user.isActive()) {
            log.info("Login attempt for disabled account id: {}", user.getId());
            throw new InvalidCredentialsException("Invalid email or password.");
        }

        // ── Step 4: Generate JWT ───────────────────────────────────────────────
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String token = jwtService.generateToken(userDetails);

        log.info("User logged in: id={}, role={}", user.getId(), user.getRole());

        return AuthResponse.builder()
                .token(token)
                .user(toUserResponse(user))
                .build();
    }

    // ─── Current User ─────────────────────────────────────────────────────────

    /**
     * Returns safe profile information for the currently authenticated user.
     *
     * Called by GET /api/auth/me — the email is extracted from the JWT
     * by the controller (via Spring Security's authentication principal).
     *
     * @param email The email of the authenticated user (from JWT subject claim)
     * @return UserResponse with safe profile data (no password)
     * @throws InvalidCredentialsException if the user is not found (shouldn't happen
     *         if JWT is valid, but defensive programming)
     */
    public UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("User not found."));
        return toUserResponse(user);
    }

    // ─── Private Helpers ──────────────────────────────────────────────────────

    /**
     * Converts a User document to a safe UserResponse DTO.
     *
     * CRITICAL: Password is NEVER included in UserResponse.
     * This is the single mapping point — every user projection goes through here.
     *
     * @param user The User MongoDB document
     * @return A safe UserResponse with no password field
     */
    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .active(user.isActive())
                .phone(user.getPhone())
                .preferredLanguage(user.getPreferredLanguage() != null ? user.getPreferredLanguage() : "en")
                .location(user.getLocation())
                .profilePhoto(user.getProfilePhoto())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
