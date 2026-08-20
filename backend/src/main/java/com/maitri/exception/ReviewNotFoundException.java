package com.maitri.exception;

/**
 * ReviewNotFoundException — thrown when a requested review cannot be found
 * or the user doesn't have permission to access it (Phase 7).
 *
 * ─── WHEN THIS IS THROWN ─────────────────────────────────────────────────────
 *   - User calls PUT/DELETE /api/reviews/{id} with an invalid review ID
 *   - User tries to update/delete a review that doesn't belong to them
 *   - Review ID exists but the review was already deleted
 *
 * ─── HTTP STATUS ─────────────────────────────────────────────────────────────
 *   Returns HTTP 404 Not Found (handled by GlobalExceptionHandler)
 *
 * ─── SECURITY CONSIDERATION ──────────────────────────────────────────────────
 *   We use 404 (not 403) even for permission issues to avoid revealing
 *   whether a review ID exists. This prevents enumeration attacks.
 *
 * ─── FRONTEND HANDLING ───────────────────────────────────────────────────────
 *   The frontend should:
 *   - Show a generic "Review not found" message
 *   - Refresh the review list (in case it was deleted by another session)
 *   - Return to the vendor detail page
 */
public class ReviewNotFoundException extends RuntimeException {
    public ReviewNotFoundException(String message) {
        super(message);
    }
}