package com.maitri.exception;

/**
 * DuplicateFavouriteException — thrown when a user tries to favourite a vendor
 * they have already favourited (Phase 8).
 *
 * ─── WHEN THIS IS THROWN ─────────────────────────────────────────────────────
 *   - User calls POST /api/favourites for a vendor already in their favourites
 *   - Business rule: one favourite per user per vendor
 *   - Also thrown when the unique compound index rejects a concurrent duplicate
 *     insert (database race safety)
 *
 * ─── HTTP STATUS ─────────────────────────────────────────────────────────────
 *   Returns HTTP 409 Conflict (handled by GlobalExceptionHandler)
 *
 * ─── FRONTEND HANDLING ───────────────────────────────────────────────────────
 *   The frontend should:
 *   - Show a message like "This vendor is already in your favourites"
 *   - Keep the button in the "favourited" state
 */
public class DuplicateFavouriteException extends RuntimeException {
    public DuplicateFavouriteException(String message) {
        super(message);
    }
}