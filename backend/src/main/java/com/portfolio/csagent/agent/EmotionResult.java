package com.portfolio.csagent.agent;

/**
 * 情绪识别结果。
 *
 * @param sentiment 情绪极性
 * @param score     负面强度 0~1（越大越负面），用于触发自动升级 / 转人工
 */
public record EmotionResult(Sentiment sentiment, double score) {

    public boolean isNegative() {
        return sentiment == Sentiment.NEGATIVE;
    }
}
