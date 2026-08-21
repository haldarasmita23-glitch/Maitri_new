package com.maitri.dto.complaint;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ComplaintUpdateRequest — DTO for editing an existing complaint (Phase 9).
 *
 * Used by:
 *   PUT /api/complaints/{id} — authenticated USER/ADMIN edits own complaint
 *
 * ─── VALIDATION RULES ────────────────────────────────────────────────────────
 *   - complaintType: Required, non-blank (category of complaint)
 *   - description: Required, non-blank, max 1000 characters
 *
 * ─── BUSINESS RULES ENFORCED ─────────────────────────────────────────────────
 *   - Users can only edit their OWN complaint (enforced in service)
 *   - Users can only edit a complaint while it is PENDING (enforced in service)
 *   - RESOLVED complaints cannot be edited (enforced in service)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintUpdateRequest {

    /**
     * Category of the complaint (e.g. "Service", "Quality", "Billing").
     * Required, non-blank.
     */
    @NotBlank(message = "Complaint type is required.")
    private String complaintType;

    /**
     * Free-text description of the complaint.
     * Required, non-blank, max 1000 characters.
     */
    @NotBlank(message = "Description is required.")
    @Size(max = 1000, message = "Description cannot exceed 1000 characters.")
    private String description;
}