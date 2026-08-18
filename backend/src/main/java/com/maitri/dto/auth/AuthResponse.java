package com.maitri.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AuthResponse — The response returned after successful login or registration.
 *
 * Contains:
 *   1. token — The JWT that the client must include in future requests.
 *              Sent as: Authorization: Bearer <token>
 *
 *   2. user  — Safe user information (see UserResponse).
 *              Used by the frontend to display profile info, determine role,
 *              and decide which UI elements to show.
 *
 * ─── WHAT IS EXCLUDED ────────────────────────────────────────────────────────
 *   - Password: NEVER included (UserResponse also excludes it)
 *   - Raw token claims: The client receives the signed JWT, not the decoded claims
 *
 * ─── CLIENT USAGE ────────────────────────────────────────────────────────────
 *   After receiving this response, the frontend should:
 *   1. Store the token (localStorage or sessionStorage)
 *   2. Include it in all subsequent API requests:
 *      Authorization: Bearer <token>
 *   3. Use user.role to conditionally show admin/vendor features
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    /**
     * The signed JWT token.
     * The client must include this in the Authorization header for all
     * protected API requests: "Authorization: Bearer <token>"
     */
    private String token;

    /**
     * Safe user information — no password, no sensitive fields.
     */
    private UserResponse user;
}
