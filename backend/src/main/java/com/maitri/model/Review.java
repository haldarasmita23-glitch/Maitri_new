package com.maitri.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Review — MongoDB Document representing a user's review of a vendor (Phase 7).
 *
 * ─── BUSINESS RULES ──────────────────────────────────────────────────────────
 *   - Only authenticated USERs can create reviews
 *   - Only APPROVED vendors can receive reviews
 *   - One review per user per vendor (enforced by unique compound index)
 *   - Rating must be between 1 and 5 (inclusive)
 *   - Review text is optional but recommended
 *   - Users can update/delete only their own reviews
 *   - When a review is created/updated/deleted, the vendor's averageRating is recalculated
 *
 * ─── COLLECTION ──────────────────────────────────────────────────────────────
 *   MongoDB collection name: "reviews"
 *
 * ─── FIELDS ──────────────────────────────────────────────────────────────────
 *   id         — MongoDB ObjectId (auto-generated, String representation)
 *   userId     — Reference to users._id (the reviewer)
 *   vendorId   — Reference to vendors._id (the vendor being reviewed)
 *   rating     — Rating from 1 to 5 stars (validated in DTO and service)
 *   reviewText — Optional review text (user's written feedback)
 *   createdAt  — When the review was first submitted
 *   updatedAt  — When the review was last modified
 *
 * ─── INDEXES ─────────────────────────────────────────────────────────────────
 *   { userId, vendorId } — Unique compound index (one review per user per vendor)
 *   { vendorId }         — Fast lookup of all reviews for a vendor
 *   { userId }           — Fast lookup of all reviews by a user
 *
 * ─── ACCESS PATTERNS ─────────────────────────────────────────────────────────
 *   - Find all reviews for a vendor (public, paginated)
 *   - Find all reviews by a user (authenticated user only)
 *   - Check if user already reviewed a vendor (before allowing new review)
 *   - Update/delete specific review (owner only)
 *
 * @Document("reviews") — Maps this class to the MongoDB "reviews" collection.
 * @CompoundIndex — Creates compound index at the database level for uniqueness.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "reviews")
@CompoundIndex(name = "user_vendor_unique", def = "{'userId': 1, 'vendorId': 1}", unique = true)
public class Review {

    /**
     * MongoDB document ID.
     * Spring Data automatically maps this to MongoDB's _id field.
     */
    @Id
    private String id;

    /**
     * Reference to the user who wrote this review.
     * Points to users._id (the reviewer).
     */
    @Indexed
    private String userId;

    /**
     * Reference to the vendor being reviewed.
     * Points to vendors._id (the business being reviewed).
     */
    @Indexed
    private String vendorId;

    /**
     * Rating from 1 to 5 stars.
     * Validated in the DTO layer to ensure it's within the valid range.
     */
    private int rating;

    /**
     * Optional review text.
     * User's written feedback about their experience with the vendor.
     * Can be null or empty - not all reviews need text.
     */
    private String reviewText;

    /**
     * Timestamp of when this review was first created.
     * Set once during review submission. Never changed after that.
     */
    private LocalDateTime createdAt;

    /**
     * Timestamp of the most recent update to this review.
     * Updated when the user edits their rating or review text.
     */
    private LocalDateTime updatedAt;
}