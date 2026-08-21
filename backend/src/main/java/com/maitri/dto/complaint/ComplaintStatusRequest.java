package com.maitri.dto.complaint;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ComplaintStatusRequest — DTO for updating a complaint's status (Phase 9).
 *
 * Used by:
 *   PATCH /api/complaints/{id}/status — VENDOR (own business) or ADMIN
 *
 * ─── VALIDATION RULES ────────────────────────────────────────────────────────
 *   - status: Required, non-blank. Must be one of PENDING | IN_PROGRESS | RESOLVED.
 *     (The enum conversion + validity check happens in the service layer, which
 *     throws InvalidComplaintStatusException → 400 for invalid values.)
 *
 * ─── BUSINESS RULES ENFORCED ─────────────────────────────────────────────────
 *   - VENDOR: PENDING → IN_PROGRESS → RESOLVED (cannot skip PENDING → RESOLVED)
 *   - ADMIN:  any transition (administrative override)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintStatusRequest {

    /**
     * The new status to apply.
     * Must be one of PENDING | IN_PROGRESS | RESOLVED.
     */
    @NotBlank(message = "Status is required.")
    private String status;
}