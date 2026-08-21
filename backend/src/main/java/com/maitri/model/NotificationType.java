package com.maitri.model;

/**
 * NotificationType — Category of an in-app notification (Phase 10).
 *
 * ─── VALUES ──────────────────────────────────────────────────────────────────
 *   GENERAL      — Platform-wide or informational messages.
 *   COMPLAINT    — The status of one of the user's complaints changed.
 *   REVIEW       — A vendor received a new review for their business.
 *   VERIFICATION — A vendor's verification status changed (approved/rejected).
 *
 * ─── WHO RECEIVES WHICH TYPE ─────────────────────────────────────────────────
 *   USER   → COMPLAINT (own complaint status updates)
 *   VENDOR → VERIFICATION (approval/rejection), REVIEW (new review on business)
 *   ADMIN  → any type, when the admin account itself is the target
 *
 * Stored in MongoDB as a plain string (enum name).
 */
public enum NotificationType {

    /** Platform-wide or informational message. */
    GENERAL,

    /** A complaint the user raised has a status update. */
    COMPLAINT,

    /** A new review was submitted for a vendor's business. */
    REVIEW,

    /** A vendor's verification status changed (approved / rejected). */
    VERIFICATION
}
