package com.maitri.exception;

/**
 * ComplaintNotFoundException — thrown when a requested complaint cannot be found
 * or the caller doesn't have permission to access it (Phase 9).
 *
 * ─── WHEN THIS IS THROWN ─────────────────────────────────────────────────────
 *   - User calls GET/PUT/DELETE /api/complaints/{id} with an invalid complaint ID
 *   - User tries to access/update/delete a complaint that doesn't belong to them
 *   - Vendor tries to access/update a complaint that isn't about their business
 *   - Complaint ID exists but the complaint was already deleted
 *
 * ─── HTTP STATUS ─────────────────────────────────────────────────────────────
 *   Returns HTTP 404 Not Found (handled by GlobalExceptionHandler)
 *
 * ─── SECURITY CONSIDERATION ──────────────────────────────────────────────────
 *   We use 404 (not 403) even for permission issues to avoid revealing
 *   whether a complaint ID exists. This prevents enumeration attacks.
 */
public class ComplaintNotFoundException extends RuntimeException {
    public ComplaintNotFoundException(String message) {
        super(message);
    }
}