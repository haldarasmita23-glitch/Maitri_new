package com.maitri.model;

/**
 * VendorStatus — Lifecycle status of a vendor listing (Phase 5).
 *
 *   PENDING   — Submitted by a VENDOR, awaiting admin review. NOT public.
 *   APPROVED  — Approved by an ADMIN. The ONLY publicly visible status.
 *   REJECTED  — Rejected by an ADMIN. Never publicly exposed.
 *
 * Stored in MongoDB as the enum name string (e.g. "APPROVED").
 */
public enum VendorStatus {
    PENDING,
    APPROVED,
    REJECTED
}