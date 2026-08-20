package com.maitri.dto.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ReviewUpdateRequest — DTO for updating an existing review (Phase 7).
 *
 * Used by:
 *   PUT /api/reviews/{reviewId} — authenticated USER updates their own review
 *
 * ─── VALIDATION RULES ────────────────────────────────────────────────────────
 *   - rating: Required, between 1 and 5 inclusive
 *   - reviewText: Optional, but if provided must be between 10 and 1000 characters
 *
 * ─── SECURITY ────────────────────────────────────────────────────────────────
 *   The reviewId comes from the URL path parameter.
 *   The userId comes from the authenticated user's JWT token.
 *   The vendorId cannot be changed (reviews are tied to a specific vendor).
 *   Users can only update their own reviews (enforced in service layer).
 *
 * ─── WHAT CAN BE UPDATED ─────────────────────────────────────────────────────
 *   - rating: Users can change their star rating
 *   - reviewText: Users can edit their written feedback
 *
 * ─── WHAT CANNOT BE UPDATED ──────────────────────────────────────────────────
 *   - vendorId: Reviews are permanently tied to a specific vendor
 *   - userId: Reviews cannot be transferred to other users
 *   - createdAt: Original creation date is preserved
 *   - updatedAt: Automatically set to current time by service layer
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewUpdateRequest {

    /**
     * Updated rating from 1 to 5 stars.
     * 1 = Very poor, 2 = Poor, 3 = Average, 4 = Good, 5 = Excellent
     */
    @NotNull(message = "Rating is required.")
    @Min(value = 1, message = "Rating must be at least 1 star.")
    @Max(value = 5, message = "Rating cannot exceed 5 stars.")
    private Integer rating;

    /**
     * Updated review text.
     * If provided, must be meaningful (at least 10 characters).
     * Limited to 1000 characters to prevent abuse.
     * Can be set to null/empty to remove existing text.
     */
    @Size(min = 10, max = 1000, message = "Review text must be between 10 and 1000 characters.")
    private String reviewText;
}