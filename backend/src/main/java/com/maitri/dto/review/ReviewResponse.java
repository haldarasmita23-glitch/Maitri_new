package com.maitri.dto.review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ReviewResponse — Safe review data projection for API responses (Phase 7).
 *
 * This DTO represents review data that is SAFE to return to clients.
 * It includes user information but excludes sensitive fields like passwords.
 *
 * ─── WHAT IS INCLUDED ────────────────────────────────────────────────────────
 *   - id: Review ID for frontend operations (edit/delete buttons)
 *   - userId: Reviewer's user ID (for ownership checks on frontend)
 *   - userName: Reviewer's display name (for attribution)
 *   - vendorId: Which vendor was reviewed (for context)
 *   - rating: The star rating (1-5)
 *   - reviewText: The written review content
 *   - createdAt: When the review was first submitted
 *   - updatedAt: When it was last modified (null if never updated)
 *
 * ─── WHAT IS EXCLUDED ────────────────────────────────────────────────────────
 *   - User password, email, or other sensitive user data
 *   - Vendor internal data beyond the ID
 *
 * ─── WHERE THIS IS USED ──────────────────────────────────────────────────────
 *   - GET /api/reviews/vendor/{vendorId} (public vendor review list)
 *   - GET /api/reviews/my (user's own review history)
 *   - POST /api/reviews (returned after creating a review)
 *   - PUT /api/reviews/{id} (returned after updating a review)
 *
 * ─── FRONTEND USAGE ──────────────────────────────────────────────────────────
 *   The frontend uses this data to:
 *   - Display reviews on vendor detail pages
 *   - Show user's review history
 *   - Enable edit/delete for user's own reviews (userId matching)
 *   - Display reviewer attribution (userName)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {

    /** Review ID for frontend operations */
    private String id;

    /** ID of the user who wrote this review */
    private String userId;

    /** Display name of the reviewer (for attribution) */
    private String userName;

    /** ID of the vendor being reviewed */
    private String vendorId;

    /** Rating from 1 to 5 stars */
    private int rating;

    /** Written review content (can be null/empty) */
    private String reviewText;

    /** When the review was first created */
    private LocalDateTime createdAt;

    /** When the review was last updated (null if never updated) */
    private LocalDateTime updatedAt;
}