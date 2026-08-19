package com.maitri.dto.auth;

import com.maitri.model.Role;
import com.maitri.model.UserLocation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * UserResponse — Safe user data projection for API responses.
 *
 * This DTO represents the user data that is SAFE to return to clients.
 * It is derived from the User model but critically excludes the password field.
 *
 * ─── WHAT IS EXCLUDED ────────────────────────────────────────────────────────
 *   - password: NEVER returned. Not even the BCrypt hash.
 *
 * ─── WHAT IS INCLUDED ────────────────────────────────────────────────────────
 *   - id:               Useful for the frontend to identify the user
 *   - name:             Display name
 *   - email:            The login identifier (returned so the client can confirm it)
 *   - role:             Needed by the frontend to show/hide admin-only features
 *   - active:           Account status
 *   - phone:            Contact phone number (Phase 6)
 *   - preferredLanguage: Preferred app language code (Phase 6)
 *   - location:         User's locality { area, city } (Phase 6)
 *   - profilePhoto:     Profile photo URL (Phase 6)
 *   - createdAt:        Account creation timestamp (useful for profile pages)
 *
 * ─── WHERE THIS IS USED ──────────────────────────────────────────────────────
 *   - Inside AuthResponse (returned after login and registration)
 *   - GET /api/auth/me (returns current authenticated user's info)
 *   - GET /api/users/me and PUT /api/users/me (Phase 6 user module)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    /** MongoDB ObjectId as a String */
    private String id;

    /** User's display name */
    private String name;

    /** User's email (login identifier) */
    private String email;

    /** User's role in the system */
    private Role role;

    /** Whether the account is currently active */
    private boolean active;

    /** User's contact phone number */
    private String phone;

    /** User's preferred app language code (e.g. "en") */
    private String preferredLanguage;

    /** User's locality — { area, city } */
    private UserLocation location;

    /** User's profile photo URL */
    private String profilePhoto;

    /** Timestamp of account creation */
    private LocalDateTime createdAt;
}
