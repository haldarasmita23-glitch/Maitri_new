package com.maitri.dto.auth;

import com.maitri.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RegisterRequest — DTO for user registration.
 *
 * This is the request body for POST /api/auth/register.
 * Contains ONLY the data a new user provides during sign-up.
 *
 * ─── VALIDATION ───────────────────────────────────────────────────────────────
 *   All validation annotations are processed by Spring's @Valid mechanism.
 *   If any field fails validation, MethodArgumentNotValidException is thrown
 *   and caught by GlobalExceptionHandler, which returns a 400 with error details.
 *
 * ─── ROLE ASSIGNMENT RULES ───────────────────────────────────────────────────
 *   - If `role` is null → defaults to Role.USER in AuthService
 *   - If `role` is Role.USER → allowed
 *   - If `role` is Role.VENDOR → allowed (self-registration as vendor)
 *   - If `role` is Role.ADMIN or Role.SUPER_ADMIN → rejected with 403 in AuthService
 *
 *   This prevents privilege escalation via the public registration endpoint.
 *
 * ─── SECURITY NOTES ──────────────────────────────────────────────────────────
 *   - This DTO is NOT the User model — it never touches MongoDB directly.
 *   - The password field here is the RAW password; AuthService hashes it.
 *   - After processing, this object is discarded. The raw password is never stored.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    /**
     * User's full name.
     *
     * Constraints:
     *   - Must not be blank (null, empty, or whitespace-only)
     *   - Must be between 2 and 50 characters
     */
    @NotBlank(message = "Name is required.")
    @Size(min = 2, max = 50, message = "Name must be between 2 and 50 characters.")
    private String name;

    /**
     * User's email address — will become their login identifier.
     *
     * Constraints:
     *   - Must not be blank
     *   - Must be a valid email format (validated by Jakarta's @Email)
     *
     * Uniqueness is verified in AuthService (not here — validation annotations
     * cannot query the database).
     */
    @NotBlank(message = "Email is required.")
    @Email(message = "Please provide a valid email address.")
    private String email;

    /**
     * User's chosen password (plain text — hashed by AuthService before storage).
     *
     * Constraints:
     *   - Must not be blank
     *   - Must be at least 8 characters
     *
     * IMPORTANT: This is the RAW password. It is NEVER stored or logged.
     * AuthService immediately hashes it with BCrypt before saving.
     */
    @NotBlank(message = "Password is required.")
    @Size(min = 8, message = "Password must be at least 8 characters.")
    private String password;

    /**
     * Requested role for the new account.
     *
     * Optional — defaults to Role.USER if not provided.
     * Only Role.USER and Role.VENDOR are accepted from public registration.
     * Passing Role.ADMIN or Role.SUPER_ADMIN results in a 403 Forbidden response.
     */
    private Role role;
}
