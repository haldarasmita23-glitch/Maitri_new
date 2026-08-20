package com.maitri.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maitri.dto.review.ReviewCreateRequest;
import com.maitri.dto.review.ReviewUpdateRequest;
import com.maitri.model.Category;
import com.maitri.model.Role;
import com.maitri.model.User;
import com.maitri.model.Vendor;
import com.maitri.model.VendorStatus;
import com.maitri.repository.CategoryRepository;
import com.maitri.repository.ReviewRepository;
import com.maitri.repository.UserRepository;
import com.maitri.repository.VendorRepository;
import com.maitri.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ReviewController Integration Tests — Phase 7 (Reviews & Ratings Module)
 *
 * ─── SCOPE ───────────────────────────────────────────────────────────────────
 *   Full-stack integration tests: Controller → Service → Repository → Embedded
 *   MongoDB (Flapdoodle). Tests the complete review workflow including
 *   authorization, validation, business rules, and rating recalculation.
 *
 * ─── COVERAGE ────────────────────────────────────────────────────────────────
 *   1.  POST /api/reviews — USER: 201 with valid review
 *   2.  POST /api/reviews — no JWT: 401
 *   3.  POST /api/reviews — VENDOR token: 403
 *   4.  POST /api/reviews — duplicate review: 409
 *   5.  POST /api/reviews — invalid rating: 400
 *   6.  POST /api/reviews — unknown vendor: 404
 *   7.  POST /api/reviews — pending vendor: 404
 *   8.  GET /api/reviews/vendor/{id} — PUBLIC: 200 with reviews
 *   9.  GET /api/reviews/vendor/{id} — pending vendor: 404
 *   10. GET /api/reviews/vendor/{id}/summary — PUBLIC: 200 with stats
 *   11. GET /api/reviews/my — USER: 200 with user's reviews
 *   12. GET /api/reviews/my — no JWT: 401
 *   13. GET /api/reviews/my — VENDOR token: 403
 *   14. PUT /api/reviews/{id} — USER: 200 with updated review
 *   15. PUT /api/reviews/{id} — another user's review: 404
 *   16. PUT /api/reviews/{id} — no JWT: 401
 *   17. DELETE /api/reviews/{id} — USER: 200
 *   18. DELETE /api/reviews/{id} — another user's review: 404
 *   19. Rating recalculation after create/update/delete
 *   20. Pagination of vendor reviews
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application.properties")
@DisplayName("ReviewController Integration Tests — Phase 7")
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    // ─── Test Constants ───────────────────────────────────────────────────────

    private static final String REVIEWS_URL = "/api/reviews";

    // ─── Test Data ────────────────────────────────────────────────────────────

    private User testUser;
    private User anotherUser;
    private User testVendor;
    private Vendor approvedVendor;
    private Vendor pendingVendor;
    private Category testCategory;

    private String userToken;
    private String anotherUserToken;
    private String vendorToken;

    @BeforeEach
    void setUp() {
        // Clean up
        reviewRepository.deleteAll();
        vendorRepository.deleteAll();
        userRepository.deleteAll();
        categoryRepository.deleteAll();

        // Create test category
        testCategory = categoryRepository.save(Category.builder()
                .categoryName("Street Food")
                .slug("street-food")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build());

        // Create test users
        testUser = userRepository.save(User.builder()
                .name("Test User")
                .email("user@test.com")
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build());

        anotherUser = userRepository.save(User.builder()
                .name("Another User")
                .email("another@test.com")
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build());

        testVendor = userRepository.save(User.builder()
                .name("Test Vendor")
                .email("vendor@test.com")
                .password(passwordEncoder.encode("password123"))
                .role(Role.VENDOR)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build());

        // Create test vendors
        approvedVendor = vendorRepository.save(Vendor.builder()
                .userId("user123")  // Not linked to avoid user conflicts
                .shopName("Test Restaurant")
                .ownerName("Owner Name")
                .categoryId(testCategory.getId())
                .description("Great food")
                .address("Test Address")
                .area("Test Area")
                .phone("9876543210")
                .openingTime("09:00")
                .closingTime("22:00")
                .averageRating(0.0)
                .status(VendorStatus.APPROVED)
                .createdAt(LocalDateTime.now())
                .build());

        pendingVendor = vendorRepository.save(Vendor.builder()
                .userId("user456")
                .shopName("Pending Restaurant")
                .ownerName("Pending Owner")
                .categoryId(testCategory.getId())
                .description("Waiting for approval")
                .address("Pending Address")
                .area("Pending Area")
                .phone("9876543211")
                .openingTime("10:00")
                .closingTime("21:00")
                .averageRating(0.0)
                .status(VendorStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build());

        // Generate JWT tokens
        userToken = jwtService.generateToken(createUserDetails(testUser.getEmail(), "ROLE_USER"));
        anotherUserToken = jwtService.generateToken(createUserDetails(anotherUser.getEmail(), "ROLE_USER"));
        vendorToken = jwtService.generateToken(createUserDetails(testVendor.getEmail(), "ROLE_VENDOR"));
    }

    private UserDetails createUserDetails(String email, String role) {
        return org.springframework.security.core.userdetails.User.builder()
                .username(email)
                .password("dummy")
                .authorities(List.of(new SimpleGrantedAuthority(role)))
                .build();
    }

    // ─── Submit Review Tests ──────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/reviews — USER: 201 with valid review")
    void submitReview_Success() throws Exception {
        ReviewCreateRequest request = ReviewCreateRequest.builder()
                .vendorId(approvedVendor.getId())
                .rating(5)
                .reviewText("Excellent food and service!")
                .build();

        mockMvc.perform(post(REVIEWS_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Review submitted successfully."))
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.reviewText").value("Excellent food and service!"))
                .andExpect(jsonPath("$.data.userName").value("Test User"))
                .andExpect(jsonPath("$.data.vendorId").value(approvedVendor.getId()));
    }

    @Test
    @DisplayName("POST /api/reviews — no JWT: 401")
    void submitReview_NoAuth_Returns401() throws Exception {
        ReviewCreateRequest request = ReviewCreateRequest.builder()
                .vendorId(approvedVendor.getId())
                .rating(5)
                .reviewText("Great food!")
                .build();

        mockMvc.perform(post(REVIEWS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/reviews — VENDOR token: 403")
    void submitReview_VendorRole_Returns403() throws Exception {
        ReviewCreateRequest request = ReviewCreateRequest.builder()
                .vendorId(approvedVendor.getId())
                .rating(5)
                .reviewText("Great food!")
                .build();

        mockMvc.perform(post(REVIEWS_URL)
                        .header("Authorization", "Bearer " + vendorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/reviews — duplicate review: 409")
    void submitReview_Duplicate_Returns409() throws Exception {
        // First review
        ReviewCreateRequest request = ReviewCreateRequest.builder()
                .vendorId(approvedVendor.getId())
                .rating(5)
                .reviewText("Great food!")
                .build();

        mockMvc.perform(post(REVIEWS_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Duplicate review attempt
        mockMvc.perform(post(REVIEWS_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("You have already reviewed this vendor. You can edit your existing review instead."));
    }

    @Test
    @DisplayName("POST /api/reviews — invalid rating: 400")
    void submitReview_InvalidRating_Returns400() throws Exception {
        ReviewCreateRequest request = ReviewCreateRequest.builder()
                .vendorId(approvedVendor.getId())
                .rating(6)  // Invalid: > 5
                .reviewText("Great food!")
                .build();

        mockMvc.perform(post(REVIEWS_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("POST /api/reviews — unknown vendor: 404")
    void submitReview_UnknownVendor_Returns404() throws Exception {
        ReviewCreateRequest request = ReviewCreateRequest.builder()
                .vendorId("nonexistent123")
                .rating(5)
                .reviewText("Great food!")
                .build();

        mockMvc.perform(post(REVIEWS_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/reviews — pending vendor: 404")
    void submitReview_PendingVendor_Returns404() throws Exception {
        ReviewCreateRequest request = ReviewCreateRequest.builder()
                .vendorId(pendingVendor.getId())
                .rating(5)
                .reviewText("Great food!")
                .build();

        mockMvc.perform(post(REVIEWS_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Reviews can only be submitted for approved vendors."));
    }

    // ─── Get Vendor Reviews Tests ──────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/reviews/vendor/{id} — PUBLIC: 200 with reviews")
    void getVendorReviews_Success() throws Exception {
        // Create some reviews first
        ReviewCreateRequest request1 = ReviewCreateRequest.builder()
                .vendorId(approvedVendor.getId())
                .rating(5)
                .reviewText("Excellent!")
                .build();

        ReviewCreateRequest request2 = ReviewCreateRequest.builder()
                .vendorId(approvedVendor.getId())
                .rating(4)
                .reviewText("Good food!")
                .build();

        // Submit reviews
        mockMvc.perform(post(REVIEWS_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(REVIEWS_URL)
                        .header("Authorization", "Bearer " + anotherUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isCreated());

        // Get reviews (no auth needed)
        mockMvc.perform(get(REVIEWS_URL + "/vendor/" + approvedVendor.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    @DisplayName("GET /api/reviews/my — USER: 200 with user's reviews")
    void getUserReviews_Success() throws Exception {
        // Create a review first
        ReviewCreateRequest request = ReviewCreateRequest.builder()
                .vendorId(approvedVendor.getId())
                .rating(5)
                .reviewText("Excellent!")
                .build();

        mockMvc.perform(post(REVIEWS_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Get user's reviews
        mockMvc.perform(get(REVIEWS_URL + "/my")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].rating").value(5))
                .andExpect(jsonPath("$.data[0].userName").value("Test User"));
    }

    @Test
    @DisplayName("PUT /api/reviews/{id} — USER: 200 with updated review")
    void updateReview_Success() throws Exception {
        // Create a review first
        ReviewCreateRequest createRequest = ReviewCreateRequest.builder()
                .vendorId(approvedVendor.getId())
                .rating(4)
                .reviewText("Good food!")
                .build();

        String response = mockMvc.perform(post(REVIEWS_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Extract review ID from response
        String reviewId = objectMapper.readTree(response).get("data").get("id").asText();

        // Update the review
        ReviewUpdateRequest updateRequest = ReviewUpdateRequest.builder()
                .rating(5)
                .reviewText("Actually, excellent food!")
                .build();

        mockMvc.perform(put(REVIEWS_URL + "/" + reviewId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.reviewText").value("Actually, excellent food!"));
    }

    @Test
    @DisplayName("DELETE /api/reviews/{id} — USER: 200")
    void deleteReview_Success() throws Exception {
        // Create a review first
        ReviewCreateRequest createRequest = ReviewCreateRequest.builder()
                .vendorId(approvedVendor.getId())
                .rating(5)
                .reviewText("Great food!")
                .build();

        String response = mockMvc.perform(post(REVIEWS_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Extract review ID from response
        String reviewId = objectMapper.readTree(response).get("data").get("id").asText();

        // Delete the review
        mockMvc.perform(delete(REVIEWS_URL + "/" + reviewId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Review deleted successfully."));
    }

    // ─── Authorization / Ownership Guards ────────────────────────────────────

    @Test
    @DisplayName("GET /api/reviews/vendor/{id} — pending vendor: 404")
    void getVendorReviews_PendingVendor_Returns404() throws Exception {
        mockMvc.perform(get(REVIEWS_URL + "/vendor/" + pendingVendor.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Reviews are not available for this vendor."));
    }

    @Test
    @DisplayName("GET /api/reviews/vendor/{id}/summary — PUBLIC: 200 with stats")
    void getVendorRatingSummary_Success() throws Exception {
        // Two reviews: 5★ and 4★
        ReviewCreateRequest r1 = ReviewCreateRequest.builder()
                .vendorId(approvedVendor.getId()).rating(5).reviewText("Excellent!")
                .build();
        ReviewCreateRequest r2 = ReviewCreateRequest.builder()
                .vendorId(approvedVendor.getId()).rating(4).reviewText("Very good!")
                .build();

        mockMvc.perform(post(REVIEWS_URL).header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(r1)))
                .andExpect(status().isCreated());
        mockMvc.perform(post(REVIEWS_URL).header("Authorization", "Bearer " + anotherUserToken)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(r2)))
                .andExpect(status().isCreated());

        mockMvc.perform(get(REVIEWS_URL + "/vendor/" + approvedVendor.getId() + "/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.averageRating").value(4.5))
                .andExpect(jsonPath("$.data.totalReviews").value(2))
                .andExpect(jsonPath("$.data.ratingDistribution.5").value(1))
                .andExpect(jsonPath("$.data.ratingDistribution.4").value(1))
                .andExpect(jsonPath("$.data.ratingDistribution.3").value(0));
    }

    @Test
    @DisplayName("GET /api/reviews/vendor/{id}/summary — pending vendor: 404")
    void getVendorRatingSummary_PendingVendor_Returns404() throws Exception {
        mockMvc.perform(get(REVIEWS_URL + "/vendor/" + pendingVendor.getId() + "/summary"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/reviews/my — no JWT: 401")
    void getUserReviews_NoAuth_Returns401() throws Exception {
        mockMvc.perform(get(REVIEWS_URL + "/my"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/reviews/my — VENDOR token: 403")
    void getUserReviews_VendorRole_Returns403() throws Exception {
        mockMvc.perform(get(REVIEWS_URL + "/my")
                        .header("Authorization", "Bearer " + vendorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("PUT /api/reviews/{id} — another user's review: 404")
    void updateReview_AnotherUsersReview_Returns404() throws Exception {
        // User A creates a review
        ReviewCreateRequest createRequest = ReviewCreateRequest.builder()
                .vendorId(approvedVendor.getId()).rating(5).reviewText("Great food!")
                .build();
        String response = mockMvc.perform(post(REVIEWS_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String reviewId = objectMapper.readTree(response).get("data").get("id").asText();

        // User B (anotherUser) cannot update it
        ReviewUpdateRequest updateRequest = ReviewUpdateRequest.builder()
                .rating(1).reviewText("Trying to hijack this review.")
                .build();
        mockMvc.perform(put(REVIEWS_URL + "/" + reviewId)
                        .header("Authorization", "Bearer " + anotherUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Review not found or access denied."));
    }

    @Test
    @DisplayName("PUT /api/reviews/{id} — no JWT: 401")
    void updateReview_NoAuth_Returns401() throws Exception {
        ReviewUpdateRequest updateRequest = ReviewUpdateRequest.builder()
                .rating(5).reviewText("Updated without auth.")
                .build();
        mockMvc.perform(put(REVIEWS_URL + "/some-review-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("DELETE /api/reviews/{id} — another user's review: 404")
    void deleteReview_AnotherUsersReview_Returns404() throws Exception {
        // User A creates a review
        ReviewCreateRequest createRequest = ReviewCreateRequest.builder()
                .vendorId(approvedVendor.getId()).rating(5).reviewText("Great food!")
                .build();
        String response = mockMvc.perform(post(REVIEWS_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String reviewId = objectMapper.readTree(response).get("data").get("id").asText();

        // User B cannot delete it
        mockMvc.perform(delete(REVIEWS_URL + "/" + reviewId)
                        .header("Authorization", "Bearer " + anotherUserToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("DELETE /api/reviews/{id} — no JWT: 401")
    void deleteReview_NoAuth_Returns401() throws Exception {
        mockMvc.perform(delete(REVIEWS_URL + "/some-review-id"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Vendor.averageRating — recalculated after create, update, and delete")
    void vendorAverageRating_Recalculated() throws Exception {
        // 1. Create 5★ → average 5.0
        ReviewCreateRequest createRequest = ReviewCreateRequest.builder()
                .vendorId(approvedVendor.getId()).rating(5).reviewText("Excellent!")
                .build();
        String createResponse = mockMvc.perform(post(REVIEWS_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String reviewId = objectMapper.readTree(createResponse).get("data").get("id").asText();

        org.assertj.core.api.Assertions.assertThat(
                vendorRepository.findById(approvedVendor.getId()).orElseThrow().getAverageRating())
                .isEqualTo(5.0);

        // 2. Update to 3★ → average 3.0
        ReviewUpdateRequest updateRequest = ReviewUpdateRequest.builder()
                .rating(3).reviewText("Average experience.")
                .build();
        mockMvc.perform(put(REVIEWS_URL + "/" + reviewId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(
                vendorRepository.findById(approvedVendor.getId()).orElseThrow().getAverageRating())
                .isEqualTo(3.0);

        // 3. Delete → no reviews → 0.0
        mockMvc.perform(delete(REVIEWS_URL + "/" + reviewId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(
                vendorRepository.findById(approvedVendor.getId()).orElseThrow().getAverageRating())
                .isEqualTo(0.0);
    }
}