package com.maitri.exception;

/**
 * DuplicateReviewException — thrown when a user tries to submit multiple reviews
 * for the same vendor (Phase 7).
 *
 * ─── WHEN THIS IS THROWN ─────────────────────────────────────────────────────
 *   - User calls POST /api/reviews for a vendor they've already reviewed
 *   - Business rule: one review per user per vendor
 *
 * ─── HTTP STATUS ─────────────────────────────────────────────────────────────
 *   Returns HTTP 409 Conflict (handled by GlobalExceptionHandler)
 *
 * ─── FRONTEND HANDLING ───────────────────────────────────────────────────────
 *   The frontend should:
 *   - Show a message like "You've already reviewed this vendor"
 *   - Offer an option to edit the existing review instead
 *   - Pre-populate the review form with the existing review data
 */
public class DuplicateReviewException extends RuntimeException {
    public DuplicateReviewException(String message) {
        super(message);
    }
}