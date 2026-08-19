package com.maitri.exception;

/**
 * Thrown when a category name (or slug) already exists.
 *
 * WHEN TRIGGERED:
 *   - POST /api/categories  with a name/slug already in use → 409 Conflict
 *   - PUT /api/categories/{id} with a name/slug already used by another category
 *
 * HANDLED BY: GlobalExceptionHandler → HTTP 409 Conflict
 */
public class DuplicateCategoryNameException extends RuntimeException {

    public DuplicateCategoryNameException(String message) {
        super(message);
    }
}