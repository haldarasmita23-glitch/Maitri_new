package com.maitri.controller;

import com.maitri.dto.ApiResponse;
import com.maitri.dto.nlp.AspectSentiment;
import com.maitri.dto.nlp.SentimentScore;
import com.maitri.exception.ReviewNotFoundException;
import com.maitri.model.Review;
import com.maitri.repository.ReviewRepository;
import com.maitri.service.NlpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.access.prepost.PreAuthorize;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * NLP Controller — Phase 13 (NLP Foundation).
 *
 * ─── ENDPOINTS & ACCESS ──────────────────────────────────────────────────────
 *   GET  /api/nlp/health                        — PUBLIC: service health check
 *   POST /api/nlp/analyze                        — USER|ADMIN: analyze text
 *   POST /api/nlp/review/{reviewId}              — USER|ADMIN: analyze existing review
 *   GET  /api/nlp/reviews/vendor/{vendorId}      — USER|ADMIN: aggregate vendor insights
 *
 * All responses use the standard ApiResponse<T> wrapper.
 * Errors throw domain-specific exceptions handled by GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/nlp")
@RequiredArgsConstructor
public class NlpController {

    private final NlpService nlpService;
    private final ReviewRepository reviewRepository;

    /**
     * GET /api/nlp/health
     * NLP service health check — no authentication required.
     */
    @GetMapping("/health")
    @PreAuthorize("permitAll()")
    @Operation(summary = "NLP service health check",
               description = "Verifies the NLP module is operational.")
    public ResponseEntity<ApiResponse<Map<String, String>>> health() {
        return ResponseEntity.ok(ApiResponse.success(
                "NLP service operational.", Map.of("status", "UP")));
    }

    /**
     * POST /api/nlp/analyze?text=...
     * Analyze free-form text for sentiment, keywords, and aspects.
     * Requires USER or ADMIN role.
     */
    @PostMapping("/analyze")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Analyze review text",
               description = "Performs sentiment analysis, keyword extraction, and aspect-based analysis on review text.",
               security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<ApiResponse<Map<String, Object>>> analyzeText(
            @RequestParam(value = "text", required = false) String text,
            @RequestParam(value = "maxKeywords", defaultValue = "10") int maxKeywords) {

        if (text == null || text.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Review text must not be blank."));
        }

        SentimentScore sentiment = nlpService.analyzeSentiment(text);
        List<String> keywords   = nlpService.extractKeywords(text, maxKeywords);
        List<AspectSentiment> aspects = nlpService.analyzeAspects(text);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sentiment", Map.of(
                "sentiment",  sentiment.getSentiment(),
                "confidence", sentiment.getConfidence()));
        result.put("keywords",   keywords);
        result.put("aspects",    aspects);
        result.put("textLength", text.length());

        return ResponseEntity.ok(ApiResponse.success("Analysis complete.", result));
    }

    /**
     * POST /api/nlp/review/{reviewId}
     * Analyze an existing review by ID.
     * Returns 404 if the review does not exist.
     */
    @PostMapping("/review/{reviewId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Analyze existing review",
               description = "Analyzes the reviewText of an existing review and returns NLP results.",
               security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<ApiResponse<Map<String, Object>>> analyzeReview(
            @PathVariable String reviewId) {

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ReviewNotFoundException(
                        "Review not found with ID: " + reviewId));

        SentimentScore sentiment = nlpService.analyzeSentiment(review.getReviewText());
        List<String> keywords   = nlpService.extractKeywords(review.getReviewText(), 10);
        List<AspectSentiment> aspects = nlpService.analyzeAspects(review.getReviewText());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reviewId",       reviewId);
        result.put("vendorId",       review.getVendorId());
        result.put("originalRating", review.getRating());
        result.put("sentiment", Map.of(
                "sentiment",  sentiment.getSentiment(),
                "confidence", sentiment.getConfidence()));
        result.put("keywords", keywords);
        result.put("aspects",  aspects);

        return ResponseEntity.ok(ApiResponse.success("Review analysis complete.", result));
    }

    /**
     * GET /api/nlp/reviews/vendor/{vendorId}
     * Aggregate NLP insights across all reviews for a vendor.
     */
    @GetMapping("/reviews/vendor/{vendorId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Aggregate vendor review insights",
               description = "Analyzes all reviews for a vendor and provides aggregated NLP insights.",
               security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<ApiResponse<Map<String, Object>>> analyzeVendorReviews(
            @PathVariable String vendorId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<Review> reviews = reviewRepository.findByVendorIdOrderByCreatedAtDesc(
                vendorId, PageRequest.of(page, size));

        List<SentimentScore> allSentiments = new ArrayList<>();
        List<String> allKeywords           = new ArrayList<>();
        Map<String, Integer> aspectCounts  = new LinkedHashMap<>();

        for (Review review : reviews.getContent()) {
            SentimentScore score = nlpService.analyzeSentiment(review.getReviewText());
            allSentiments.add(score);

            nlpService.extractKeywords(review.getReviewText(), 5).stream()
                    .filter(kw -> !allKeywords.contains(kw))
                    .forEach(allKeywords::add);

            nlpService.analyzeAspects(review.getReviewText()).forEach(aspect -> {
                String key = aspect.getAspect() + ":" + aspect.getSentiment();
                aspectCounts.merge(key, 1, Integer::sum);
            });
        }

        long positiveCount = allSentiments.stream()
                .filter(s -> "positive".equals(s.getSentiment())).count();
        long negativeCount = allSentiments.stream()
                .filter(s -> "negative".equals(s.getSentiment())).count();
        long neutralCount  = allSentiments.size() - positiveCount - negativeCount;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("vendorId",        vendorId);
        result.put("totalReviews",    reviews.getTotalElements());
        result.put("analyzedReviews", allSentiments.size());
        result.put("sentimentDistribution", Map.of(
                "positive", positiveCount,
                "negative", negativeCount,
                "neutral",  neutralCount));
        result.put("topKeywords", allKeywords.subList(0, Math.min(allKeywords.size(), 10)));
        result.put("aspectInsights", aspectCounts.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(5)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new)));

        return ResponseEntity.ok(ApiResponse.success("Vendor insights retrieved.", result));
    }
}