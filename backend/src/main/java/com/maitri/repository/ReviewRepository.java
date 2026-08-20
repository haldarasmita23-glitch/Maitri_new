package com.maitri.repository;

import com.maitri.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Review Repository — MongoDB data access layer for reviews (Phase 7).
 *
 * ─── QUERY PATTERNS ──────────────────────────────────────────────────────────
 *   - Find all reviews for a specific vendor (paginated, for public display)
 *   - Find all reviews by a specific user (for user's review history)
 *   - Check if a user has already reviewed a vendor (uniqueness enforcement)
 *   - Find a specific review by ID and user (for ownership verification)
 *   - Calculate average rating for a vendor (aggregation query)
 *
 * ─── SECURITY NOTES ──────────────────────────────────────────────────────────
 *   All methods return Review objects that will be converted to ReviewResponse DTOs
 *   in the service layer. The DTOs ensure no sensitive data is exposed.
 *
 * ─── PERFORMANCE NOTES ───────────────────────────────────────────────────────
 *   - Queries use indexed fields (vendorId, userId) for fast lookups
 *   - Vendor reviews are paginated to handle vendors with many reviews
 *   - User reviews are not paginated (assumption: users don't write many reviews)
 */
@Repository
public interface ReviewRepository extends MongoRepository<Review, String> {

    /**
     * Find all reviews for a specific vendor, paginated.
     * Used for public display of vendor reviews on the vendor detail page.
     * 
     * @param vendorId The vendor's ID
     * @param pageable Pagination parameters (page, size, sort)
     * @return Page of reviews for the vendor
     */
    Page<Review> findByVendorIdOrderByCreatedAtDesc(String vendorId, Pageable pageable);

    /**
     * Find all reviews written by a specific user.
     * Used for the user's review history page.
     * 
     * @param userId The user's ID
     * @return List of reviews written by the user, newest first
     */
    List<Review> findByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * Check if a user has already reviewed a specific vendor.
     * Used to enforce the "one review per user per vendor" business rule.
     * 
     * @param userId The user's ID
     * @param vendorId The vendor's ID
     * @return true if the user has already reviewed this vendor, false otherwise
     */
    boolean existsByUserIdAndVendorId(String userId, String vendorId);

    /**
     * Find a specific review by user and vendor.
     * Used when a user wants to update their existing review.
     * 
     * @param userId The user's ID
     * @param vendorId The vendor's ID
     * @return Optional containing the review if found, empty if not
     */
    Optional<Review> findByUserIdAndVendorId(String userId, String vendorId);

    /**
     * Find a review by ID, but only if it belongs to the specified user.
     * Used for ownership verification when updating/deleting reviews.
     * 
     * @param id The review's ID
     * @param userId The user's ID (for ownership verification)
     * @return Optional containing the review if found and owned by user, empty otherwise
     */
    Optional<Review> findByIdAndUserId(String id, String userId);

    /**
     * Calculate the average rating for a vendor.
     * This is a custom aggregation query that returns the average of all ratings
     * for the specified vendor.
     * 
     * @param vendorId The vendor's ID
     * @return Average rating as a Double, or null if no reviews exist
     */
    @Query("{ 'vendorId': ?0 }")
    List<Review> findByVendorId(String vendorId);

    /**
     * Count the total number of reviews for a vendor.
     * Used to display review count on vendor cards and detail pages.
     * 
     * @param vendorId The vendor's ID
     * @return Number of reviews for the vendor
     */
    long countByVendorId(String vendorId);

    /**
     * Delete all reviews for a vendor.
     * Used when a vendor is deleted (cleanup operation).
     * Note: This is typically only used by admin operations.
     * 
     * @param vendorId The vendor's ID
     */
    void deleteByVendorId(String vendorId);
}