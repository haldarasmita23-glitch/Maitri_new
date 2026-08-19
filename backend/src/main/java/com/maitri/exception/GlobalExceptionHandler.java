package com.maitri.exception;

import com.maitri.dto.ApiResponse;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
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
     * Handles duplicate email registration attempts.
     *
     * WHEN TRIGGERED:
     *   AuthService.register() finds an existing user with the same email.
     *
     * HTTP Status: 409 Conflict
     */
    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateEmail(DuplicateEmailException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Handles failed login attempts (wrong email or password).
     *
     * WHEN TRIGGERED:
     *   AuthService.login() cannot find the user or password does not match.
     *
     * SECURITY: Message is always generic — never reveals whether email or password was wrong.
     *
     * HTTP Status: 401 Unauthorized
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidCredentials(InvalidCredentialsException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Handles attempts to access a resource the authenticated user is not allowed to use.
     *
     * WHEN TRIGGERED:
     *   An authenticated user tries to access an endpoint that requires a higher role.
     *   Example: a USER trying to call an ADMIN-only endpoint.
     *
     * HTTP Status: 403 Forbidden
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("You do not have permission to perform this action."));
    }

    /**
     * Handles category operations that reference a category which does not exist.
     *
     * WHEN TRIGGERED:
     *   PUT /api/categories/{id} or PATCH /api/categories/{id}/disable with an unknown id.
     *
     * HTTP Status: 404 Not Found
     */
    @ExceptionHandler(CategoryNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleCategoryNotFound(CategoryNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Handles attempts to create/update a category with a name or slug already in use.
     *
     * WHEN TRIGGERED:
     *   POST /api/categories with a duplicate name/slug, or
     *   PUT /api/categories/{id} whose new name/slug belongs to another category.
     *
     * HTTP Status: 409 Conflict
     */
    @ExceptionHandler(DuplicateCategoryNameException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateCategoryName(
            DuplicateCategoryNameException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Handles vendor operations that reference a vendor which does not exist,
     * or a vendor that is not publicly visible (PENDING/REJECTED).
     *
     * WHEN TRIGGERED:
     *   GET /api/vendors/{id} with an unknown or non-APPROVED id, or
     *   GET/PUT /api/vendors/me when the account has no profile yet, or
     *   PATCH /api/vendors/{id}/approve|reject with an unknown id.
     *
     * HTTP Status: 404 Not Found
     */
    @ExceptionHandler(VendorNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleVendorNotFound(VendorNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Handles user operations where the authenticated user cannot be resolved.
     *
     * WHEN TRIGGERED:
     *   GET/PUT /api/users/me when the JWT subject email has no matching user.
     *   (Defensive — protects against deleted accounts holding stale tokens.)
     *
     * HTTP Status: 404 Not Found
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserNotFound(UserNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Handles a VENDOR account trying to create a second vendor profile.
     *
     * WHEN TRIGGERED:
     *   POST /api/vendors/apply when the authenticated user already has a profile.
     *
     * HTTP Status: 409 Conflict
     */
    @ExceptionHandler(DuplicateVendorProfileException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateVendorProfile(
            DuplicateVendorProfileException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Handles vendor operations that reference a category that cannot be used.
     *
     * WHEN TRIGGERED:
     *   POST /api/vendors/apply or PUT /api/vendors/me with a DISABLED category.
     *   (An UNKNOWN category slug throws CategoryNotFoundException → 404 instead.)
     *
     * HTTP Status: 400 Bad Request
     */
    @ExceptionHandler(InvalidCategoryException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidCategory(InvalidCategoryException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Handles authentication failures (unauthenticated access to protected endpoints).
     *
     * WHEN TRIGGERED:
     *   A request reaches a protected endpoint without a valid JWT.
     *   Spring Security's AuthenticationEntryPoint propagates this.
     *
     * HTTP Status: 401 Unauthorized
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Authentication required. Please log in."));
    }

    /**
     * Handles JWT token expiration.
     *
     * WHEN TRIGGERED:
     *   A request is made with a valid but expired JWT token.
     *
     * HTTP Status: 401 Unauthorized
     */
    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<ApiResponse<Void>> handleExpiredJwt(ExpiredJwtException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Your session has expired. Please log in again."));
    }

    /**
     * Handles invalid JWT tokens (malformed, wrong signature, etc.).
     *
     * WHEN TRIGGERED:
     *   A request is made with a tampered or malformed JWT token.
     *
     * HTTP Status: 401 Unauthorized
     */
    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ApiResponse<Void>> handleJwtException(JwtException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid authentication token. Please log in again."));
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
