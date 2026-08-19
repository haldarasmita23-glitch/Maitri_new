package com.maitri.exception;

/**
 * Thrown when a category operation references a category that does not exist.
 *
 * WHEN TRIGGERED:
 *   - PUT /api/categories/{id}        with an unknown id
 *   - PATCH /api/categories/{id}/disable with an unknown id
 *
 * HANDLED BY: GlobalExceptionHandler → HTTP 404 Not Found
 */
public class CategoryNotFoundException extends RuntimeException {

    public CategoryNotFoundException(String message) {
        super(message);
    }
}