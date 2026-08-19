package com.maitri.exception;

/**
 * Thrown when a vendor operation references a category that cannot be used.
 *
 * WHEN TRIGGERED:
 *   - The requested category is disabled (active = false) during apply/update.
 *
 * (An UNKNOWN category slug throws CategoryNotFoundException → 404 instead.)
 *
 * HANDLED BY: GlobalExceptionHandler → HTTP 400 Bad Request
 */
public class InvalidCategoryException extends RuntimeException {

    public InvalidCategoryException(String message) {
        super(message);
    }
}