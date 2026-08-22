package com.maitri.dto.nlp;

/**
 * SentimentScore — Sentiment analysis result with confidence score.
 */
public class SentimentScore {

    private String sentiment; // "positive", "negative", "neutral"
    private double confidence; // 0.0 to 1.0

    public SentimentScore() {}

    public SentimentScore(String sentiment, double confidence) {
        this.sentiment = sentiment;
        this.confidence = confidence;
    }

    public String getSentiment() { return sentiment; }
    public void setSentiment(String sentiment) { this.sentiment = sentiment; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
}