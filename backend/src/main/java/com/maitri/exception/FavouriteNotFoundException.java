package com.maitri.exception;

/**
 * FavouriteNotFoundException — thrown when a favourite cannot be found for the
 * authenticated user (Phase 8).
 *
 * ─── WHEN THIS IS THROWN ─────────────────────────────────────────────────────
 *   - User calls DELETE /api/favourites/{vendorId} for a vendor that is not in
 *     their favourites
 *
 * ─── HTTP STATUS ─────────────────────────────────────────────────────────────
 *   Returns HTTP 404 Not Found (handled by GlobalExceptionHandler)
 *
 * ─── SECURITY CONSIDERATION ──────────────────────────────────────────────────
 *   Operations are always scoped to the authenticated user. A 404 (rather than
 *   a per-user leak) ensures no information is revealed about another user's
 *   favourite list.
 *
 * ─── FRONTEND HANDLING ───────────────────────────────────────────────────────
 *   The frontend should:
 *   - Treat the button as "not favourited" and refresh its state
 */
public class FavouriteNotFoundException extends RuntimeException {
    public FavouriteNotFoundException(String message) {
        super(message);
    }
}