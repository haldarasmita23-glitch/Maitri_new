package com.maitri.exception;

/**
 * Thrown when a VENDOR account attempts to create a second vendor profile.
 *
 * WHEN TRIGGERED:
 *   - POST /api/vendors/apply when the authenticated user already has a profile
 *
 * The vendors.userId field is unique (1:1 with users._id), so only one
 * business listing is allowed per account.
 *
 * HANDLED BY: GlobalExceptionHandler → HTTP 409 Conflict
 */
public class DuplicateVendorProfileException extends RuntimeException {

    public DuplicateVendorProfileException(String message) {
        super(message);
    }
}