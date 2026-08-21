package com.maitri.exception;

/**
 * NotificationNotFoundException — thrown when a notification cannot be found
 * for the authenticated account (Phase 10).
 *
 * ─── WHEN THIS IS THROWN ─────────────────────────────────────────────────────
 *   - PUT /api/notifications/{id}/read for an unknown id, or a notification
 *     that belongs to a different account
 *
 * ─── HTTP STATUS ─────────────────────────────────────────────────────────────
 *   Returns HTTP 404 Not Found (handled by GlobalExceptionHandler)
 *
 * ─── SECURITY CONSIDERATION ──────────────────────────────────────────────────
 *   Operations are always scoped to the authenticated account. A 404 (rather
 *   than a 403) ensures no information is revealed about the existence of
 *   another account's notifications.
 *
 * ─── FRONTEND HANDLING ───────────────────────────────────────────────────────
 *   The frontend should refresh its notification list — the notification was
 *   removed or never existed for this account.
 */
public class NotificationNotFoundException extends RuntimeException {
    public NotificationNotFoundException(String message) {
        super(message);
    }
}
