package com.portfolio.csagent.agent;

/** score is negative intensity; confidence is classifier confidence. */
public record EmotionResult(Sentiment sentiment, double score, double confidence, String source) {
    public EmotionResult(Sentiment sentiment, double score) {
        this(sentiment, score, 0.9, "rules");
    }

    public boolean isNegative() {
        return sentiment == Sentiment.NEGATIVE;
    }
}
