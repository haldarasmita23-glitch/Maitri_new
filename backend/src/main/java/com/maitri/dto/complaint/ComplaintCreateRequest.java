package com.maitri.dto.complaint;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ComplaintCreateRequest — DTO for raising a new complaint (Phase 9).
 *
 * Used by:
 *   POST /api/complaints — authenticated USER/ADMIN raises a complaint
 *
 * ─── VALIDATION RULES ────────────────────────────────────────────────────────
 *   - vendorId: Required (which vendor is being complained about)
 *   - complaintType: Required, non-blank (category of complaint)
 *   - description: Required, non-blank, max 1000 characters
 *
 * ─── SECURITY ────────────────────────────────────────────────────────────────
 *   The userId is NOT included in this DTO — it comes from the authenticated
 *   user's JWT token. This prevents users from raising complaints on behalf
 *   of other users.
 *
 * ─── BUSINESS RULES ENFORCED ─────────────────────────────────────────────────
 *   - Only APPROVED vendors can be complained about (enforced in service)
 *   - Multiple complaints by the same user against the same vendor are ALLOWED
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintCreateRequest {

    /**
     * ID of the vendor being complained about.
     * Must be a valid, APPROVED vendor ID.
     */
    @NotBlank(message = "Vendor ID is required.")
    private String vendorId;

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