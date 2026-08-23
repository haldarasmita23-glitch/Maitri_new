package com.maitri.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maitri.dto.nlp.AspectSentiment;
import com.maitri.dto.nlp.SentimentScore;
import com.maitri.exception.ReviewNotFoundException;
import com.maitri.model.Review;
import com.maitri.model.Role;
import com.maitri.model.User;
import com.maitri.model.Vendor;
import com.maitri.model.VendorStatus;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NLP Controller Integration Tests — Phase 13
 *
 * ─── SCOPE ───────────────────────────────────────────────────────────────────
 *   Full-stack integration tests: Controller → Service → Repository → Embedded
 *   MongoDB (Flapdoodle). Tests the NLP endpoints for text analysis.
 *
 * ─── COVERAGE ────────────────────────────────────────────────────────────────
 *   1.  GET /api/nlp/health — public (no JWT) → 200
 *   2.  POST /api/nlp/analyze — no JWT → 401
 *   3.  POST /api/nlp/analyze — USER → 200 with analysis
 *   4.  POST /api/nlp/analyze — ADMIN → 200
 *   5.  POST /api/nlp/analyze — VENDOR → 403
 *   6.  POST /api/nlp/analyze — empty text → 400
 *   7.  POST /api/nlp/analyze — missing text param → 400
 *   8.  POST /api/nlp/review/{id} — valid review → 200
 *   9.  POST /api/nlp/review/{id} — nonexistent review → 404
 *   10. POST /api/nlp/review/{id} — no JWT → 401
 *   11. GET /api/nlp/reviews/vendor/{id} — valid vendor → 200
 *   12. GET /api/nlp/reviews/vendor/{id} — nonexistent vendor → 200 with empty results
 *   13. GET /api/nlp/reviews/vendor/{id} — no JWT → 401
 *   14. VENDOR denied on analyze endpoints → 403
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application.properties")
@DisplayName("NLP Controller Integration Tests — Phase 13")
class NlpControllerTest {

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    // ─── Test Constants ───────────────────────────────────────────────────────

    private static final String NLP_URL = "/api/nlp";

    private User testUser;
    private User adminUser;
    private User vendorUser;
    private Vendor approvedVendor;
    private Review testReview;

    private String userToken;
    private String adminToken;
    private String vendorToken;

    @BeforeEach
    void setUp() {
        reviewRepository.deleteAll();
        userRepository.deleteAll();
        vendorRepository.deleteAll();

        // Create test users
        testUser = userRepository.save(User.builder()
                .name("Test User")
                .email("user@test.com")
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build());

        adminUser = userRepository.save(User.builder()
                .name("Test Admin")
                .email("admin@test.com")
                .password(passwordEncoder.encode("password123"))
                .role(Role.ADMIN)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build());

        vendorUser = userRepository.save(User.builder()
                .name("Test Vendor")
                .email("vendor@test.com")
                .password(passwordEncoder.encode("password123"))
                .role(Role.VENDOR)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build());

        // Create approved vendor
        approvedVendor = vendorRepository.save(Vendor.builder()
                .userId(vendorUser.getId())
                .shopName("Test Restaurant")
                .ownerName("Owner Name")
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

        // Create a test review
        testReview = reviewRepository.save(Review.builder()
                .userId(testUser.getId())
                .vendorId(approvedVendor.getId())
                .rating(5)
                .reviewText("Excellent food and service!")
                .createdAt(LocalDateTime.now())
                .build());

        // Generate JWT tokens
        userToken = jwtService.generateToken(createUserDetails(testUser.getEmail(), "ROLE_USER"));
        adminToken = jwtService.generateToken(createUserDetails(adminUser.getEmail(), "ROLE_ADMIN"));
        vendorToken = jwtService.generateToken(createUserDetails(vendorUser.getEmail(), "ROLE_VENDOR"));
    }

    private UserDetails createUserDetails(String email, String role) {
        return org.springframework.security.core.userdetails.User.builder()
                .username(email)
                .password("dummy")
                .authorities(List.of(new SimpleGrantedAuthority(role)))
                .build();
    }

    // ─── Health Endpoint ──────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/nlp/health — public: 200 with status UP")
    void healthEndpoint_isPublicAndReturns200() throws Exception {
        mockMvc.perform(get(NLP_URL + "/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    // ─── Analyze Text Endpoint ────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/nlp/analyze — no JWT: 401 Unauthorized")
    void analyzeText_noJwt_returns401() throws Exception {
        mockMvc.perform(post(NLP_URL + "/analyze")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("text", "Great food!"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/nlp/analyze — VENDOR: 403 Forbidden")
    void analyzeText_vendorRole_returns403() throws Exception {
        mockMvc.perform(post(NLP_URL + "/analyze")
                        .header("Authorization", "Bearer " + vendorToken)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("text", "Great food!"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/nlp/analyze — USER: 200 with sentiment, keywords, aspects")
    void analyzeText_userRole_returns200WithAnalysis() throws Exception {
        mockMvc.perform(post(NLP_URL + "/analyze")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("text", "Excellent food and great service!")
                        .param("maxKeywords", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Analysis complete."))
                .andExpect(jsonPath("$.data.sentiment.sentiment").exists())
                .andExpect(jsonPath("$.data.sentiment.confidence").exists())
                .andExpect(jsonPath("$.data.keywords").isArray())
                .andExpect(jsonPath("$.data.aspects").isArray())
                .andExpect(jsonPath("$.data.textLength").value(33));
    }

    @Test
    @DisplayName("POST /api/nlp/analyze — ADMIN: 200 with analysis")
    void analyzeText_adminRole_returns200() throws Exception {
        mockMvc.perform(post(NLP_URL + "/analyze")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("text", "Terrible service and awful food.")
                        .param("maxKeywords", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sentiment.sentiment").value("negative"));
    }

    @Test
    @DisplayName("POST /api/nlp/analyze — empty text: 400 Bad Request")
    void analyzeText_emptyText_returns400() throws Exception {
        mockMvc.perform(post(NLP_URL + "/analyze")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("text", "")
                        .param("maxKeywords", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("blank")));
    }

    @Test
    @DisplayName("POST /api/nlp/analyze — missing text param: 400 Bad Request")
    void analyzeText_missingText_returns400() throws Exception {
        mockMvc.perform(post(NLP_URL + "/analyze")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("maxKeywords", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("blank")));
    }

    // ─── Analyze Review Endpoint ──────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/nlp/review/{id} — USER: 200 analyzes existing review")
    void analyzeReview_validReview_returns200() throws Exception {
        mockMvc.perform(post(NLP_URL + "/review/" + testReview.getId())
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Review analysis complete."))
                .andExpect(jsonPath("$.data.reviewId").value(testReview.getId()))
                .andExpect(jsonPath("$.data.vendorId").value(approvedVendor.getId()))
                .andExpect(jsonPath("$.data.originalRating").value(5))
                .andExpect(jsonPath("$.data.sentiment.sentiment").exists())
                .andExpect(jsonPath("$.data.keywords").isArray())
                .andExpect(jsonPath("$.data.aspects").isArray());
    }

    @Test
    @DisplayName("POST /api/nlp/review/{id} — nonexistent review: 404 Not Found")
    void analyzeReview_nonexistentReview_returns404() throws Exception {
        mockMvc.perform(post(NLP_URL + "/review/nonexistent-id")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/nlp/review/{id} — no JWT: 401 Unauthorized")
    void analyzeReview_noJwt_returns401() throws Exception {
        mockMvc.perform(post(NLP_URL + "/review/" + testReview.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── Vendor Reviews Aggregate Endpoint ────────────────────────────────────

    @Test
    @DisplayName("GET /api/nlp/reviews/vendor/{id} — USER: 200 with aggregated insights")
    void analyzeVendorReviews_validVendor_returns200() throws Exception {
        // Create another review for the same vendor
        reviewRepository.save(Review.builder()
                .userId(adminUser.getId())
                .vendorId(approvedVendor.getId())
                .rating(4)
                .reviewText("Good food but slow service.")
                .createdAt(LocalDateTime.now())
                .build());

        mockMvc.perform(get(NLP_URL + "/reviews/vendor/" + approvedVendor.getId())
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Vendor insights retrieved."))
                .andExpect(jsonPath("$.data.vendorId").value(approvedVendor.getId()))
                .andExpect(jsonPath("$.data.totalReviews").value(2))
                .andExpect(jsonPath("$.data.analyzedReviews").value(2))
                .andExpect(jsonPath("$.data.sentimentDistribution.positive").exists())
                .andExpect(jsonPath("$.data.topKeywords").isArray())
                .andExpect(jsonPath("$.data.aspectInsights").isMap());
    }

    @Test
    @DisplayName("GET /api/nlp/reviews/vendor/{id} — nonexistent vendor: 200 with empty results")
    void analyzeVendorReviews_nonexistentVendor_returns200WithEmptyResults() throws Exception {
        mockMvc.perform(get(NLP_URL + "/reviews/vendor/nonexistent-id")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.vendorId").value("nonexistent-id"))
                .andExpect(jsonPath("$.data.totalReviews").value(0))
                .andExpect(jsonPath("$.data.analyzedReviews").value(0))
                .andExpect(jsonPath("$.data.topKeywords").isEmpty());
    }

    @Test
    @DisplayName("GET /api/nlp/reviews/vendor/{id} — no JWT: 401 Unauthorized")
    void analyzeVendorReviews_noJwt_returns401() throws Exception {
        mockMvc.perform(get(NLP_URL + "/reviews/vendor/" + approvedVendor.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
}