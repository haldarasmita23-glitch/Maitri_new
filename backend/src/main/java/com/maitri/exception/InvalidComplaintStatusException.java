package com.maitri.exception;

/**
 * InvalidComplaintStatusException — thrown when an invalid complaint status is
 * requested or an illegal status transition is attempted (Phase 9).
 *
 * ─── WHEN THIS IS THROWN ─────────────────────────────────────────────────────
 *   - A status update request contains a value that is not
 *     PENDING | IN_PROGRESS | RESOLVED (e.g. "URGENT" or blank)
 *   - A VENDOR attempts to skip a state (e.g. PENDING → RESOLVED directly,
 *     bypassing IN_PROGRESS)
 *   - A status transition is attempted from a terminal state
 *
 * ─── HTTP STATUS ─────────────────────────────────────────────────────────────
 *   Returns HTTP 400 Bad Request (handled by GlobalExceptionHandler)
 */
public class InvalidComplaintStatusException extends RuntimeException {
    public InvalidComplaintStatusException(String message) {
        super(message);
    }
}