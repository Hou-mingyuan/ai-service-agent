package com.portfolio.csagent.agent;

public enum Sentiment {
    POSITIVE("正面"),
    NEUTRAL("中性"),
    NEGATIVE("负面");

    private final String label;

    Sentiment(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
