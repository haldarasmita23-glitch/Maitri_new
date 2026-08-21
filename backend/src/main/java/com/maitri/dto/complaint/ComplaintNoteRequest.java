package com.maitri.dto.complaint;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ComplaintNoteRequest — DTO for adding/updating an admin note (Phase 9).
 *
 * Used by:
 *   PATCH /api/complaints/{id}/note — ADMIN only
 *
 * ─── VALIDATION RULES ────────────────────────────────────────────────────────
 *   - adminNote: Required, non-blank, max 2000 characters.
 *
 * ─── SECURITY ─────────────────────────────────────────────────────────────────
 *   The adminNote is an INTERNAL admin comment. It is stored on the complaint
 *   but is NEVER exposed in public ComplaintResponse projections (only in the
 *   admin-facing response shape).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintNoteRequest {

    /**
     * The admin's internal note about this complaint.
     * Required, non-blank, max 2000 characters.
     */
    @NotBlank(message = "Admin note is required.")
    @Size(max = 2000, message = "Admin note cannot exceed 2000 characters.")
    private String adminNote;
}