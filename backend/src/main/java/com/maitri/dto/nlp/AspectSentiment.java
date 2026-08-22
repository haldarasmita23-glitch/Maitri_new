package com.maitri.dto.nlp;

/**
 * AspectSentiment — Aspect-based sentiment analysis result.
 * Associates a sentiment with a specific aspect of a review (e.g., food, pricing, staff).
 */
public class AspectSentiment {

    private String aspect; // e.g., "food", "pricing", "delivery", "quality", "staff"
    private String sentiment; // "positive", "negative", "neutral"
    private double confidence; // 0.0 to 1.0

    public AspectSentiment() {}

    public AspectSentiment(String aspect, String sentiment, double confidence) {
        this.aspect = aspect;
        this.sentiment = sentiment;
        this.confidence = confidence;
    }

    public String getAspect() { return aspect; }
    public void setAspect(String aspect) { this.aspect = aspect; }
    public String getSentiment() { return sentiment; }
    public void setSentiment(String sentiment) { this.sentiment = sentiment; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
}