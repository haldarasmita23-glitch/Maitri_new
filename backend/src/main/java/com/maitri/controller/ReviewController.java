package com.maitri.controller;

import com.maitri.dto.ApiResponse;
import com.maitri.dto.review.ReviewCreateRequest;
import com.maitri.dto.review.ReviewResponse;
import com.maitri.dto.review.ReviewUpdateRequest;
import com.maitri.dto.review.VendorRatingsSummary;
import com.maitri.exception.UserNotFoundException;
import com.maitri.model.User;
import com.maitri.repository.UserRepository;
import com.maitri.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Review Controller — Phase 7 (Reviews & Ratings Module).
 *
 * ─── ENDPOINTS & ACCESS ──────────────────────────────────────────────────────
 *   POST   /api/reviews                     — USER only: submit review for vendor
 *   GET    /api/reviews/vendor/{vendorId}   — PUBLIC: paginated reviews for vendor
 *   GET    /api/reviews/my                  — USER only: user's review history
 *   PUT    /api/reviews/{reviewId}          — USER only: update own review
 *   DELETE /api/reviews/{reviewId}          — USER only: delete own review
 *   GET    /api/reviews/vendor/{vendorId}/summary — PUBLIC: rating statistics
 *
 * ─── AUTHORIZATION MATRIX ────────────────────────────────────────────────────
 *   | Endpoint                    | Anonymous | USER | VENDOR | ADMIN |
 *   |-----------------------------|-----------|------|--------|-------|
 *   | POST /api/reviews           | 401       | ✅   | 403    | ✅    |
 *   | GET /api/reviews/vendor/*   | ✅        | ✅   | ✅     | ✅    |
 *   | GET /api/reviews/my         | 401       | ✅   | 403    | ✅    |
 *   | PUT /api/reviews/*          | 401       | ✅   | 403    | ✅    |
 *   | DELETE /api/reviews/*       | 401       | ✅   | 403    | ✅    |
 *   | GET .../summary             | ✅        | ✅   | ✅     | ✅    |
 *
 * VENDOR accounts are deliberately DENIED from review operations:
 * - Business accounts should not write reviews as regular users
 * - This maintains review authenticity and prevents conflicts of interest
 * - VENDORs can see reviews of their own business via vendor management endpoints
 *
 * Role enforcement is split across two layers (defense in depth):
 *   - SecurityConfig  requires authentication at the HTTP layer (401 for anonymous)
 *   - @PreAuthorize   enforces exact roles at method layer (403 for wrong role)
 *
 * ─── PAGINATION ──────────────────────────────────────────────────────────────
 *   Vendor reviews support pagination to handle businesses with many reviews.
 *   Default: 10 reviews per page, sorted by newest first.
 *   User reviews are not paginated (assumption: users don't write many reviews).
 */
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final UserRepository userRepository;

    /** USER only — submits a review for a vendor. */
    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ReviewResponse>> submitReview(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ReviewCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success("Review submitted successfully.",
                        reviewService.submitReview(currentUser(userDetails), request))
        );
    }

    /** PUBLIC — gets paginated reviews for a vendor. */
    @GetMapping("/vendor/{vendorId}")
    public ResponseEntity<ApiResponse<Page<ReviewResponse>>> getVendorReviews(
            @PathVariable String vendorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<ReviewResponse> reviews = reviewService.getVendorReviews(vendorId, pageable);
        
        return ResponseEntity.ok(
                ApiResponse.success("Vendor reviews retrieved.", reviews)
        );
    }

    /** PUBLIC — gets rating summary statistics for a vendor. */
    @GetMapping("/vendor/{vendorId}/summary")
    public ResponseEntity<ApiResponse<VendorRatingsSummary>> getVendorRatingSummary(
            @PathVariable String vendorId) {
        return ResponseEntity.ok(
                ApiResponse.success("Vendor rating summary retrieved.",
                        reviewService.getVendorRatings(vendorId))
        );
    }

    /** USER only — gets the authenticated user's review history. */
    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getUserReviews(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                ApiResponse.success("Your reviews retrieved.",
                        reviewService.getUserReviews(currentUser(userDetails)))
        );
    }

    /** USER only — updates the authenticated user's own review. */
    @PutMapping("/{reviewId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ReviewResponse>> updateReview(
            @PathVariable String reviewId,
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ReviewUpdateRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success("Review updated successfully.",
                        reviewService.updateReview(reviewId, currentUser(userDetails), request))
        );
    }

    /** USER only — deletes the authenticated user's own review. */
    @DeleteMapping("/{reviewId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteReview(
            @PathVariable String reviewId,
            @AuthenticationPrincipal UserDetails userDetails) {
        reviewService.deleteReview(reviewId, currentUser(userDetails));
        return ResponseEntity.ok(
                ApiResponse.success("Review deleted successfully.")
        );
    }

    /** Resolves the authenticated UserDetails (email) to a User document. */
    private User currentUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new UserNotFoundException("User not found."));
    }
}