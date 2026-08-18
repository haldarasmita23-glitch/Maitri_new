package com.maitri.exception;

/**
 * DuplicateEmailException — Thrown when a registration attempt uses an email
 * address that is already associated with an existing account.
 *
 * ─── WHEN THROWN ─────────────────────────────────────────────────────────────
 *   AuthService.register() checks existsByEmail() before saving.
 *   If the email is already taken, this exception is thrown.
 *
 * ─── HOW IT IS HANDLED ───────────────────────────────────────────────────────
 *   GlobalExceptionHandler catches this and returns:
 *     HTTP 409 Conflict
 *     { "success": false, "message": "An account with this email already exists." }
 *
 * ─── SECURITY NOTE ───────────────────────────────────────────────────────────
 *   Returning 409 on duplicate email does reveal that the email is registered.
 *   This is an accepted trade-off for this type of platform — users need to
 *   know to use a different email or to log in instead.
 *   For higher-security applications, a generic "check your email" approach is
 *   preferred, but that adds complexity and is not required for Maitri Phase 3.
 *
 * Extends RuntimeException (unchecked) so it does not need to be declared
 * in method signatures — Spring's exception handler catches it automatically.
 */
public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String message) {
        super(message);
    }
}
