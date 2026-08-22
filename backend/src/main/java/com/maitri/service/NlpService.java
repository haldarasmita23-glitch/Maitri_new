package com.maitri.service;

import com.maitri.dto.nlp.AspectSentiment;
import com.maitri.dto.nlp.SentimentScore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

/**
 * NlpService — Core NLP processing for review analysis.
 * Provides text preprocessing, stemming, sentiment analysis, keyword extraction,
 * and aspect-based review analysis. Designed to be modular and
 * extensible for Phase 14 natural-language search and recommendations.
 * All processing is rule-based (no external model dependencies) to
 * keep the production build simple and deterministic.
 * Preprocessing pipeline: lowercase → trim → remove extra whitespace → tokenize
 * → remove stop-words → stem → join back to string.
 */
@Service
@RequiredArgsConstructor
public class NlpService {

    // Fixed English stop-words set for token filtering
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "a", "an", "the", "and", "or", "but", "is", "are", "was", "were",
            "be", "been", "being", "have", "has", "had", "do", "does", "did",
            "will", "would", "should", "could", "may", "might", "must",
            "i", "you", "he", "she", "it", "we", "they", "me", "him", "her",
            "us", "them", "my", "your", "his", "her", "its", "our", "their",
            "this", "that", "these", "those", "there", "not", "no", "nor",
            "so", "than", "too", "very", "just", "now", "also", "often",
            "always", "never", "usually", "particularly", "mainly", "certainly",
            "possibly", "probably", "unfortunately", "happily"
    ));

    // English Stemmer — rule-based suffix stripping
    // Uses common English suffixes: -ed, -ing, -es, -s, -ly, -ment, -tion.
    // Self-contained: no external NLP library dependencies required.
    private static final Set<String> STEM_SUFFIXES = new HashSet<>(Arrays.asList(
            "sses", "ies", "us", "s", "ed", "ing", "ly", "ment", "tion", "er", "est"
    ));

    /** Stem a single word by removing common English suffixes. */
    private String stemWord(String word) {
        if (word == null || word.length() <= 3) {
            return word.toLowerCase();
        }

        String result = word.toLowerCase();

        // Try longer suffixes first for more accurate stemming
        for (String suffix : STEM_SUFFIXES) {
            if (result.endsWith(suffix)) {
                String base = result.substring(0, result.length() - suffix.length());
                // Basic stemming: replace -ies with -y (e.g., carries -> carry)
                if (suffix.equals("ies") && base.length() > 0 && base.endsWith("i")) {
                    result = base.substring(0, base.length() - 1) + "y";
                }
                // Don't stem further after first match
                return result.isEmpty() ? word : result;
            }
        }
        return result;
    }

    // Aspect keywords mapping to review topics.
    // Each entry maps an aspect name to its associated keyword patterns.
    private static final Map<String, Set<String>> ASPECT_KEYWORDS = new HashMap<>(Map.of(
            "food",     new HashSet<>(Arrays.asList("taste", "flavor", "spicy", "bland", "delicious", "food")),
            "pricing",  new HashSet<>(Arrays.asList("price", "cost", "expensive", "cheap", "overpriced", "pricing")),
            "delivery", new HashSet<>(Arrays.asList("delivery", "shipping", "fast", "slow", "late")),
            "quality",  new HashSet<>(Arrays.asList("quality", "workmanship", "material", "build")),
            "staff",    new HashSet<>(Arrays.asList("staff", "service", "worker", "employee", "attendant"))
    ));

    /** Default confidence threshold for sentiment/aspect determination. */
    private static final double CONFIDENCE_THRESHOLD = 0.5;

    /**
     * Preprocesses text: lowercases, tokenizes, removes stop-words, stems,
     * and joins back into a clean string.
     *
     * @param rawText The original review text
     * @return Cleaned, stemmed, tokenized text ready for analysis
     */
    public String preprocessText(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return "";
        }

        String text = rawText.toLowerCase().trim();

        // Remove extra whitespace
        text = text.replaceAll("\\s+", " ");

        // Tokenize and filter stop-words, then stem each token
        List<String> tokens = Arrays.stream(text.split(" "))
                .filter(token -> !STOP_WORDS.contains(token) && !token.isEmpty())
                .map(this::stemWord)
                .collect(Collectors.toList());

        // Join tokens back into a clean string
        return String.join(" ", tokens);
    }

    /**
     * Analyzes the sentiment of the given text.
     * Uses a rule-based keyword approach:
     * - Positive keywords: good, excellent, great, amazing, wonderful, etc.
     * - Negative keywords: poor, bad, terrible, awful, etc.
     * - Neutral: neither positive nor negative keywords found
     *
     * @param text The text to analyze (already preprocessed recommended)
     * @return SentimentScore with sentiment and confidence
     */
    public SentimentScore analyzeSentiment(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new SentimentScore("neutral", 1.0);
        }

        // Positive keywords and their weights
        Set<String> positiveKeywords = new HashSet<>(Arrays.asList(
                "good", "great", "excellent", "awesome", "amazing", "wonderful",
                "fantastic", "perfect", "love", "loved", "enjoy", "enjoyed",
                "best", "super", "impressed", "impressive"
        ));

        // Negative keywords and their weights
        Set<String> negativeKeywords = new HashSet<>(Arrays.asList(
                "bad", "poor", "terrible", "awful", "hate", "hated",
                "worst", "disappointing", "disappointed", "angry", "frustrating",
                "waste", "useless"
        ));

        // Tokenize the text — fix: use explicit loop, not Arrays.asList().filter()
        Set<String> tokens = new HashSet<>();
        for (String token : text.toLowerCase().split("\\s+")) {
            if (!STOP_WORDS.contains(token) && !token.isEmpty()) {
                tokens.add(token);
            }
        }

        int positiveCount = 0;
        int negativeCount = 0;

        for (String token : tokens) {
            if (positiveKeywords.contains(token)) {
                positiveCount++;
            }
            if (negativeKeywords.contains(token)) {
                negativeCount++;
            }
        }

        String sentiment;
        double confidence;

        if (positiveCount > negativeCount) {
            sentiment = "positive";
            confidence = Math.min(1.0, 0.5 + (positiveCount - negativeCount) * 0.15);
        } else if (negativeCount > positiveCount) {
            sentiment = "negative";
            confidence = Math.min(1.0, 0.5 + (negativeCount - positiveCount) * 0.15);
        } else {
            sentiment = "neutral";
            confidence = 0.5;
        }

        // Ensure confidence is at least CONFIDENCE_THRESHOLD
        if (confidence < CONFIDENCE_THRESHOLD) {
            confidence = CONFIDENCE_THRESHOLD;
            if (positiveCount == negativeCount && tokens.isEmpty()) {
                sentiment = "neutral";
            }
        }

        return new SentimentScore(sentiment, confidence);
    }

    /**
     * Extracts keywords from the text based on frequency.
     * Returns the top N most frequent non-stopword tokens.
     *
     * @param text The text to extract keywords from
     * @param maxKeywords Maximum number of keywords to return
     * @return List of keyword strings
     */
    public List<String> extractKeywords(String text, int maxKeywords) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // Tokenize and count frequencies — fix: use explicit loop, not Arrays.asList().filter()
        Map<String, Long> frequencyMap = new HashMap<>();
        for (String token : text.toLowerCase().split("\\s+")) {
            if (!STOP_WORDS.contains(token) && !token.isEmpty()) {
                frequencyMap.put(token, frequencyMap.getOrDefault(token, 0L) + 1);
            }
        }

        // Sort by frequency (descending) and return top N
        // fix: use Map.Entry.comparingByValue() and Map.Entry::getValue/getKey
        return frequencyMap.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(maxKeywords)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Performs aspect-based sentiment analysis on the text.
     * Identifies which aspects (food, pricing, delivery, quality, staff) are
     * mentioned and associates sentiment with each.
     *
     * @param text The text to analyze (already preprocessed recommended)
     * @return List of AspectSentiment results
     */
    public List<AspectSentiment> analyzeAspects(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // Preprocess the text for aspect matching
        String processed = preprocessText(text);

        List<AspectSentiment> results = new ArrayList<>();

        // For each aspect, check if aspect keywords appear in the text
        // and determine sentiment based on surrounding tokens
        for (Map.Entry<String, Set<String>> entry : ASPECT_KEYWORDS.entrySet()) {
            String aspect = entry.getKey();
            Set<String> keywords = entry.getValue();

            // Check if any aspect keyword appears in the text
            boolean aspectFound = keywords.stream().anyMatch(
                    token -> processed.contains(token)
            );

            if (aspectFound) {
                // Analyze sentiment for this aspect
                SentimentScore score = analyzeSentiment(text);

                // Determine confidence based on how many aspect keywords appear
                int keywordMatchCount = (int) keywords.stream()
                        .filter(token -> processed.contains(token))
                        .count();

                double aspectConfidence = CONFIDENCE_THRESHOLD;
                if (keywordMatchCount >= 2) {
                    aspectConfidence = Math.min(1.0, CONFIDENCE_THRESHOLD + 0.2);
                } else if (keywordMatchCount >= 1) {
                    aspectConfidence = CONFIDENCE_THRESHOLD + 0.1;
                }

                results.add(new AspectSentiment(aspect, score.getSentiment(), aspectConfidence));
            }
        }

        // If no aspects were found but there are meaningful tokens,
        // add a general "miscellaneous" aspect
        if (results.isEmpty() && !text.trim().isEmpty()) {
            SentimentScore score = analyzeSentiment(text);
            results.add(new AspectSentiment("miscellaneous", score.getSentiment(), score.getConfidence()));
        }

        return results;
    }
}