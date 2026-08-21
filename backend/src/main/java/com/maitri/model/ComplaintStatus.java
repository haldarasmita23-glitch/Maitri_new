package com.maitri.model;

/**
 * ComplaintStatus — lifecycle states for a complaint (Phase 9).
 *
 * ─── STATES ──────────────────────────────────────────────────────────────────
 *   PENDING      — Complaint submitted by a USER; awaiting vendor/admin action.
 *   IN_PROGRESS  — Vendor has acknowledged and is working on the complaint.
 *   RESOLVED     — Complaint has been resolved (final state).
 *
 * ─── TRANSITIONS ─────────────────────────────────────────────────────────────
 *   VENDOR:  PENDING → IN_PROGRESS → RESOLVED   (cannot skip PENDING → RESOLVED)
 *   ADMIN:   any transition (administrative override)
 *
 * ─── EDITABILITY ─────────────────────────────────────────────────────────────
 *   USER may edit/delete a complaint only while it is PENDING.
 *   Once RESOLVED, the complaint is locked for user edits.
 */
public enum ComplaintStatus {
    PENDING,
    IN_PROGRESS,
    RESOLVED
}