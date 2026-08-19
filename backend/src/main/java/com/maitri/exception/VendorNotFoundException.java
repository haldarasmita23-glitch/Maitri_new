package com.maitri.exception;

/**
 * Thrown when a vendor operation references a vendor that does not exist,
 * OR references a vendor that is not publicly visible (PENDING/REJECTED).
 *
 * WHEN TRIGGERED:
 *   - GET  /api/vendors/{id}           with an unknown or non-APPROVED id
 *   - GET/PUT /api/vendors/me          when the account has no profile yet
 *   - PATCH /api/vendors/{id}/approve  /reject with an unknown id
 *
 * HANDLED BY: GlobalExceptionHandler → HTTP 404 Not Found
 */
public class VendorNotFoundException extends RuntimeException {

    public VendorNotFoundException(String message) {
        super(message);
    }
}