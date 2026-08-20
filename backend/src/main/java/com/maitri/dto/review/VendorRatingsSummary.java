package com.maitri.dto.review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * VendorRatingsSummary — Aggregated rating statistics for a vendor (Phase 7).
 *
 * This DTO provides summary statistics about a vendor's reviews and ratings.
 * Used to display rating summaries on vendor detail pages and vendor cards.
 *
 * ─── WHAT IS INCLUDED ────────────────────────────────────────────────────────
 *   - averageRating: Overall average rating (e.g., 4.2)
 *   - totalReviews: Total number of reviews
 *   - ratingDistribution: Count of each rating (1-5 stars)
 *
 * ─── CALCULATION LOGIC ───────────────────────────────────────────────────────
 *   - averageRating: Sum of all ratings / number of reviews (rounded to 1 decimal)
 *   - totalReviews: Count of all reviews for this vendor
 *   - ratingDistribution: Count how many reviews gave 1 star, 2 stars, etc.
 *
 * ─── WHERE THIS IS USED ──────────────────────────────────────────────────────
 *   - Vendor detail pages (rating summary section)
 *   - Vendor cards (quick rating display)
 *   - Vendor listings (sorting by rating)
 *
 * ─── FRONTEND USAGE ──────────────────────────────────────────────────────────
 *   The frontend uses this data to:
 *   - Display overall rating with stars
 *   - Show total review count
 *   - Render rating distribution bars/charts
 *   - Enable sorting vendors by rating
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorRatingsSummary {

    /** Overall average rating (0.0 to 5.0, rounded to 1 decimal place) */
    private double averageRating;

    /** Total number of reviews for this vendor */
    private long totalReviews;

    /**
     * Distribution of ratings by star count.
     * Key: rating value (1, 2, 3, 4, 5)
     * Value: count of reviews with that rating
     * 
     * Example:
     * {
     *   "1": 2,   // 2 reviews gave 1 star
     *   "2": 1,   // 1 review gave 2 stars
     *   "3": 5,   // 5 reviews gave 3 stars
     *   "4": 8,   // 8 reviews gave 4 stars
     *   "5": 12   // 12 reviews gave 5 stars
     * }
     */
    private Map<Integer, Long> ratingDistribution;
}