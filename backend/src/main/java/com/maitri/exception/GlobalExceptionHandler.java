package com.maitri.exception;

import com.maitri.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Global Exception Handler for Maitri Backend.
 *
 * ─── WHY THIS EXISTS ───────────────────────────────────────────────────────
 *
 * Without this class, Spring Boot returns its own default error format:
 *   {
 *     "timestamp": 1234567890,
 *     "status": 400,
 *     "error": "Bad Request",
 *     "path": "/api/something"
 *   }
 *
 * This is inconsistent with our ApiResponse format.
 *
 * With GlobalExceptionHandler, ALL errors (validation, not found, server errors)
 * are caught here and returned in our standard ApiResponse format:
 *   {
 *     "success": false,
 *     "message": "Validation failed. Please check your input.",
 *     "errors": ["Email is required", "Name must be at least 2 characters"],
 *     "timestamp": "2026-08-13T14:30:00"
 *   }
 *
 * ─── HOW IT WORKS ──────────────────────────────────────────────────────────
 *
 * @RestControllerAdvice: Makes this class a global interceptor.
 *   When any @RestController throws an exception, Spring looks in this class
 *   for a matching @ExceptionHandler method and calls it instead of crashing.
 *
 * @ExceptionHandler(SomeException.class): Handles that specific exception type.
 *
 * ─── SECURITY NOTE ─────────────────────────────────────────────────────────
 *   Never expose internal stack traces or system details to API clients.
 *   Log full error details server-side; return only safe, generic messages.
 * ───────────────────────────────────────────────────────────────────────────
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles Bean Validation errors (@Valid failed on a request body).
     *
     * WHEN TRIGGERED:
     *   A controller receives a request body with invalid data.
     *   Example: registration with a blank name or invalid email format.
     *
     * WHAT WE DO:
     *   Collect all field error messages and return them as a list,
     *   so the frontend can show specific feedback for each invalid field.
     *
     * HTTP Status: 400 Bad Request
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.toList());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Validation failed. Please check your input.", errors));
    }

    /**
     * Handles requests to endpoints that don't exist (404 Not Found).
     *
     * WHEN TRIGGERED:
     *   A client calls an API path that doesn't exist.
     *   Example: GET /api/unknown-path
     *
     * HTTP Status: 404 Not Found
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("The requested resource was not found."));
    }

    /**
     * Handles requests using wrong HTTP method (405 Method Not Allowed).
     *
     * WHEN TRIGGERED:
     *   A client uses POST on a GET-only endpoint, for example.
     *
     * HTTP Status: 405 Method Not Allowed
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error("HTTP method '" + ex.getMethod() + "' is not supported for this endpoint."));
    }

    /**
     * Catch-all handler for any other unexpected exceptions.
     *
     * WHEN TRIGGERED:
     *   Any exception that is not caught by a more specific handler above.
     *   Example: database connection failure, null pointer exception, etc.
     *
     * SECURITY NOTE:
     *   We return a generic message to the client.
     *   We log the real error server-side for debugging.
     *   Never expose internal error details (stack trace, class names) to clients.
     *
     * HTTP Status: 500 Internal Server Error
     *
     * TODO Phase 13: Replace System.err with a proper SLF4J logger.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        // Log the real error for server-side debugging
        // In Phase 13, this will become: log.error("Unhandled exception", ex);
        System.err.println("[MAITRI ERROR] Unhandled exception: "
                + ex.getClass().getSimpleName() + ": " + ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(
                        "An unexpected error occurred. Our team has been notified. Please try again later."
                ));
    }
}
