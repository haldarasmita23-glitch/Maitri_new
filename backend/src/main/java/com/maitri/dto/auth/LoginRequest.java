package com.maitri.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LoginRequest — DTO for user login.
 *
 * This is the request body for POST /api/auth/login.
 *
 * ─── VALIDATION ───────────────────────────────────────────────────────────────
 *   Both fields are required. Invalid email format is rejected before
 *   any database lookup, providing a fast-fail on obviously wrong input.
 *
 * ─── SECURITY NOTES ──────────────────────────────────────────────────────────
 *   - The password field is the raw user-entered password.
 *   - AuthService compares it against the BCrypt hash stored in MongoDB.
 *   - On failure, we return a generic "Invalid credentials" message —
 *     we do NOT indicate whether the email or password was wrong,
 *     to prevent user enumeration attacks.
 *   - This DTO is never stored or logged.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    /**
     * The user's email address (login identifier).
     */
    @NotBlank(message = "Email is required.")
    @Email(message = "Please provide a valid email address.")
    private String email;

    /**
     * The user's password (plain text for comparison against stored BCrypt hash).
     *
     * IMPORTANT: This is never stored or logged.
     */
    @NotBlank(message = "Password is required.")
    private String password;
}
