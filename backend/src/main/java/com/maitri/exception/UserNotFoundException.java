package com.maitri.exception;

/**
 * Thrown when the authenticated user cannot be resolved from the JWT.
 *
 * WHEN TRIGGERED:
 *   - GET /api/users/me when the JWT subject email has no matching user
 *   - PUT /api/users/me when the JWT subject email has no matching user
 *
 * This is defensive — a valid JWT should always correspond to an existing
 * user — but it protects against deleted accounts holding stale tokens.
 *
 * HANDLED BY: GlobalExceptionHandler → HTTP 404 Not Found
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String message) {
        super(message);
    }
}
