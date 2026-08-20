package com.maitri.service;

import com.maitri.dto.review.ReviewCreateRequest;
import com.maitri.dto.review.ReviewResponse;
import com.maitri.dto.review.ReviewUpdateRequest;
import com.maitri.dto.review.VendorRatingsSummary;
import com.maitri.exception.DuplicateReviewException;
import com.maitri.exception.ReviewNotFoundException;
import com.maitri.exception.VendorNotFoundException;
import com.maitri.model.Review;
import com.maitri.model.User;
import com.maitri.model.Vendor;
import com.maitri.model.VendorStatus;
import com.maitri.repository.ReviewRepository;
import com.maitri.repository.UserRepository;
import com.maitri.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Review Service — business logic for the Reviews & Ratings Module (Phase 7).
 *
 * ─── BUSINESS RULES ──────────────────────────────────────────────────────────
 *   1. Only authenticated USERs can create reviews (enforced by controller)
 *   2. Only APPROVED vendors can receive reviews
 *   3. One review per user per vendor (enforced by unique compound index + service check)
 *   4. Rating must be between 1 and 5 (enforced by DTO validation)
 *   5. Users can update/delete only their own reviews
 *   6. Vendor's averageRating is recalculated whenever reviews are created/updated/deleted
 *
 * ─── METHODS ─────────────────────────────────────────────────────────────────
 *   submitReview()       — USER: create a new review for a vendor
 *   getVendorReviews()   — PUBLIC: paginated reviews for a vendor
 *   getUserReviews()     — USER: all reviews written by the authenticated user
 *   updateReview()       — USER: update own review
 *   deleteReview()       — USER: delete own review
 *   getVendorRatings()   — PUBLIC: rating summary statistics for a vendor
 *
 * ─── RATING CALCULATION ──────────────────────────────────────────────────────
 *   The vendor's averageRating field is kept in sync whenever reviews change.
 *   This denormalization improves performance for vendor listings and search.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final VendorRepository vendorRepository;
    private final UserRepository userRepository;

    /**
     * Creates a new review for a vendor.
     *
     * @param user    The authenticated user (role=USER, enforced by controller)
     * @param request Review details (vendorId, rating, reviewText)
     * @return The created review
     * @throws VendorNotFoundException    if vendor doesn't exist or isn't APPROVED
     * @throws DuplicateReviewException  if user has already reviewed this vendor
     */
    @Transactional
    public ReviewResponse submitReview(User user, ReviewCreateRequest request) {
        // Verify vendor exists and is approved
        Vendor vendor = vendorRepository.findById(request.getVendorId())
                .orElseThrow(() -> new VendorNotFoundException("Vendor not found."));

        if (vendor.getStatus() != VendorStatus.APPROVED) {
            throw new VendorNotFoundException("Reviews can only be submitted for approved vendors.");
        }

        // Check for duplicate review
        if (reviewRepository.existsByUserIdAndVendorId(user.getId(), request.getVendorId())) {
            throw new DuplicateReviewException(
                    "You have already reviewed this vendor. You can edit your existing review instead."
            );
        }

        // Create the review
        Review review = Review.builder()
                .userId(user.getId())
                .vendorId(request.getVendorId())
                .rating(request.getRating())
                .reviewText(request.getReviewText())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Review savedReview = reviewRepository.save(review);

        // Recalculate vendor's average rating
        updateVendorRating(request.getVendorId());

        log.info("[Review] Created: reviewId={}, userId={}, vendorId={}, rating={}",
                savedReview.getId(), user.getId(), request.getVendorId(), request.getRating());

        return toResponse(savedReview, user);
    }

    /**
     * Gets paginated reviews for a vendor.
     * Only returns reviews for APPROVED vendors.
     *
     * @param vendorId The vendor's ID
     * @param pageable Pagination parameters
     * @return Page of reviews with user information
     * @throws VendorNotFoundException if vendor doesn't exist or isn't APPROVED
     */
    public Page<ReviewResponse> getVendorReviews(String vendorId, Pageable pageable) {
        // Verify vendor exists and is approved
        Vendor vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new VendorNotFoundException("Vendor not found."));

        if (vendor.getStatus() != VendorStatus.APPROVED) {
            throw new VendorNotFoundException("Reviews are not available for this vendor.");
        }

        Page<Review> reviews = reviewRepository.findByVendorIdOrderByCreatedAtDesc(vendorId, pageable);

        return reviews.map(review -> {
            // Get user info for attribution (safe to do since reviews are public)
            User reviewer = userRepository.findById(review.getUserId()).orElse(null);
            return toResponse(review, reviewer);
        });
    }

    /**
     * Gets all reviews written by the authenticated user.
     *
     * @param user The authenticated user
     * @return List of user's reviews, newest first
     */
    public List<ReviewResponse> getUserReviews(User user) {
        List<Review> reviews = reviewRepository.findByUserIdOrderByCreatedAtDesc(user.getId());

        return reviews.stream()
                .map(review -> toResponse(review, user))
                .collect(Collectors.toList());
    }

    /**
     * Updates an existing review.
     * Only the review owner can update their review.
     *
     * @param reviewId The review's ID
     * @param user     The authenticated user
     * @param request  Updated review details
     * @return The updated review
     * @throws ReviewNotFoundException if review doesn't exist or doesn't belong to user
     */
    @Transactional
    public ReviewResponse updateReview(String reviewId, User user, ReviewUpdateRequest request) {
        Review review = reviewRepository.findByIdAndUserId(reviewId, user.getId())
                .orElseThrow(() -> new ReviewNotFoundException("Review not found or access denied."));

        // Update the review
        review.setRating(request.getRating());
        review.setReviewText(request.getReviewText());
        review.setUpdatedAt(LocalDateTime.now());

        Review savedReview = reviewRepository.save(review);

        // Recalculate vendor's average rating
        updateVendorRating(review.getVendorId());

        log.info("[Review] Updated: reviewId={}, userId={}, newRating={}",
                reviewId, user.getId(), request.getRating());

        return toResponse(savedReview, user);
    }

    /**
     * Deletes a review.
     * Only the review owner can delete their review.
     *
     * @param reviewId The review's ID
     * @param user     The authenticated user
     * @throws ReviewNotFoundException if review doesn't exist or doesn't belong to user
     */
    @Transactional
    public void deleteReview(String reviewId, User user) {
        Review review = reviewRepository.findByIdAndUserId(reviewId, user.getId())
                .orElseThrow(() -> new ReviewNotFoundException("Review not found or access denied."));

        String vendorId = review.getVendorId();
        reviewRepository.deleteById(reviewId);

        // Recalculate vendor's average rating
        updateVendorRating(vendorId);

        log.info("[Review] Deleted: reviewId={}, userId={}, vendorId={}",
                reviewId, user.getId(), vendorId);
    }

    /**
     * Gets rating summary statistics for a vendor.
     *
     * @param vendorId The vendor's ID
     * @return Rating summary with average, count, and distribution
     * @throws VendorNotFoundException if vendor doesn't exist or isn't APPROVED
     */
    public VendorRatingsSummary getVendorRatings(String vendorId) {
        // Verify vendor exists and is approved
        Vendor vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new VendorNotFoundException("Vendor not found."));

        if (vendor.getStatus() != VendorStatus.APPROVED) {
            throw new VendorNotFoundException("Rating summary is not available for this vendor.");
        }

        List<Review> reviews = reviewRepository.findByVendorId(vendorId);

        if (reviews.isEmpty()) {
            return VendorRatingsSummary.builder()
                    .averageRating(0.0)
                    .totalReviews(0L)
                    .ratingDistribution(Map.of(1, 0L, 2, 0L, 3, 0L, 4, 0L, 5, 0L))
                    .build();
        }

        // Calculate average rating
        double averageRating = reviews.stream()
                .mapToInt(Review::getRating)
                .average()
                .orElse(0.0);

        // Round to 1 decimal place
        averageRating = BigDecimal.valueOf(averageRating)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();

        // Calculate rating distribution
        Map<Integer, Long> ratingDistribution = reviews.stream()
                .collect(Collectors.groupingBy(Review::getRating, Collectors.counting()));

        // Ensure all ratings (1-5) are present in the map
        for (int i = 1; i <= 5; i++) {
            ratingDistribution.putIfAbsent(i, 0L);
        }

        return VendorRatingsSummary.builder()
                .averageRating(averageRating)
                .totalReviews((long) reviews.size())
                .ratingDistribution(ratingDistribution)
                .build();
    }

    /**
     * Recalculates and updates a vendor's average rating.
     * Called whenever reviews are created, updated, or deleted.
     *
     * @param vendorId The vendor's ID
     */
    private void updateVendorRating(String vendorId) {
        List<Review> reviews = reviewRepository.findByVendorId(vendorId);

        double newAverageRating = 0.0;
        if (!reviews.isEmpty()) {
            newAverageRating = reviews.stream()
                    .mapToInt(Review::getRating)
                    .average()
                    .orElse(0.0);

            // Round to 1 decimal place
            newAverageRating = BigDecimal.valueOf(newAverageRating)
                    .setScale(1, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        // Update vendor's average rating
        final double finalAverageRating = newAverageRating;
        final int reviewCount = reviews.size();
        vendorRepository.findById(vendorId).ifPresent(vendor -> {
            vendor.setAverageRating(finalAverageRating);
            vendorRepository.save(vendor);
            log.debug("[Review] Updated vendor rating: vendorId={}, newRating={}, reviewCount={}",
                    vendorId, finalAverageRating, reviewCount);
        });
    }

    /**
     * Converts a Review entity to a ReviewResponse DTO.
     *
     * @param review The review entity
     * @param user   The user who wrote the review (for name attribution)
     * @return Safe review data projection
     */
    private ReviewResponse toResponse(Review review, User user) {
        return ReviewResponse.builder()
                .id(review.getId())
                .userId(review.getUserId())
                .userName(user != null ? user.getName() : "Unknown User")
                .vendorId(review.getVendorId())
                .rating(review.getRating())
                .reviewText(review.getReviewText())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}