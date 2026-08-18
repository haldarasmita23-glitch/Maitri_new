package com.maitri.exception;

/**
 * InvalidCredentialsException — Thrown when a login attempt fails due to
 * incorrect email or password.
 *
 * ─── WHEN THROWN ─────────────────────────────────────────────────────────────
 *   AuthService.login() throws this when:
 *   - No user exists with the provided email
 *   - The provided password does not match the stored BCrypt hash
 *
 * ─── HOW IT IS HANDLED ───────────────────────────────────────────────────────
 *   GlobalExceptionHandler catches this and returns:
 *     HTTP 401 Unauthorized
 *     { "success": false, "message": "Invalid email or password." }
 *
 * ─── SECURITY — USER ENUMERATION PREVENTION ─────────────────────────────────
 *   CRITICAL: The response message is ALWAYS "Invalid email or password."
 *   We NEVER tell the caller whether the email doesn't exist or the password
 *   was wrong. This prevents attackers from using the login endpoint to
 *   discover which email addresses have accounts on the platform.
 *
 * Extends RuntimeException (unchecked) so Spring's @ExceptionHandler
 * catches it without needing try/catch in every service method.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
