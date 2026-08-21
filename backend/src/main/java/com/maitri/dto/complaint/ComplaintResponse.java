package com.maitri.dto.complaint;

import com.maitri.model.ComplaintStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ComplaintResponse — Safe complaint data projection for API responses (Phase 9).
 *
 * ─── WHAT IS INCLUDED ────────────────────────────────────────────────────────
 *   - id: Complaint ID for frontend operations (edit/delete buttons)
 *   - userId: Complainant's user ID (for ownership checks on frontend)
 *   - userName: Complainant's display name (for attribution)
 *   - vendorId: Which vendor was complained about (for context)
 *   - vendorName: The vendor's shop name (for display)
 *   - complaintType: Category of complaint
 *   - description: The complaint text
 *   - status: PENDING | IN_PROGRESS | RESOLVED
 *   - adminNote: Internal admin comment — ONLY populated for ADMIN callers.
 *     For USER/VENDOR callers this is always null (never exposed publicly).
 *   - createdAt / updatedAt: Timestamps
 *
 * ─── WHAT IS EXCLUDED ────────────────────────────────────────────────────────
 *   - User password, email, or other sensitive user data
 *   - Vendor email, password, or other sensitive vendor data
 *   - JWT tokens or any authentication material
 *
 * ─── SECURITY NOTE ───────────────────────────────────────────────────────────
 *   The adminNote is an INTERNAL admin comment. The service layer populates it
 *   ONLY when the caller is an ADMIN. For USER and VENDOR callers it is always
 *   null, so the internal note is never leaked to non-admin roles.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintResponse {

    /** Complaint ID for frontend operations */
    private String id;

    /** ID of the user who raised this complaint */
    private String userId;

    /** Display name of the complainant (for attribution) */
    private String userName;

    /** ID of the vendor being complained about */
    private String vendorId;

    /** Shop name of the vendor being complained about (for display) */
    private String vendorName;

    /** Category of the complaint */
    private String complaintType;

    /** Free-text description of the complaint */
    private String description;

    /** Lifecycle state: PENDING | IN_PROGRESS | RESOLVED */
    private ComplaintStatus status;

    /** Internal admin comment — ONLY populated for ADMIN callers, else null */
    private String adminNote;

    /** When the complaint was first raised */
    private LocalDateTime createdAt;

    /** When the complaint was last modified */
    private LocalDateTime updatedAt;
}