package com.maitri.dto.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ReviewCreateRequest — DTO for creating a new review (Phase 7).
 *
 * Used by:
 *   POST /api/reviews — authenticated USER submits a review for a vendor
 *
 * ─── VALIDATION RULES ────────────────────────────────────────────────────────
 *   - vendorId: Required (which vendor is being reviewed)
 *   - rating: Required, between 1 and 5 inclusive
 *   - reviewText: Optional, but if provided must be between 10 and 1000 characters
 *
 * ─── SECURITY ────────────────────────────────────────────────────────────────
 *   The userId is NOT included in this DTO - it comes from the authenticated
 *   user's JWT token. This prevents users from submitting reviews on behalf
 *   of other users.
 *
 * ─── BUSINESS RULES ENFORCED ─────────────────────────────────────────────────
 *   - Rating scale: 1-5 stars (consistent with UI star components)
 *   - Review text minimum: 10 characters (encourages meaningful feedback)
 *   - Review text maximum: 1000 characters (prevents abuse/spam)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewCreateRequest {

    /**
     * ID of the vendor being reviewed.
     * Must be a valid, approved vendor ID.
     */
    @NotBlank(message = "Vendor ID is required.")
    private String vendorId;

    /**
     * Rating from 1 to 5 stars.
     * 1 = Very poor, 2 = Poor, 3 = Average, 4 = Good, 5 = Excellent
     */
    @NotNull(message = "Rating is required.")
    @Min(value = 1, message = "Rating must be at least 1 star.")
    @Max(value = 5, message = "Rating cannot exceed 5 stars.")
    private Integer rating;

    /**
     * Optional review text.
     * If provided, must be meaningful (at least 10 characters).
     * Limited to 1000 characters to prevent abuse.
     */
    @Size(min = 10, max = 1000, message = "Review text must be between 10 and 1000 characters.")
    private String reviewText;
}