package com.portfolio.csagent.agent;

import org.springframework.stereotype.Service;

/**
 * 情绪识别：基于情感词典的轻量打分。负面强度用于自动升级与转人工决策。
 */
@Service
public class EmotionService {

    private static final String[] STRONG_NEGATIVE = {
            "投诉", "愤怒", "气死", "垃圾", "骗子", "曝光", "315", "差评", "恶心", "退钱",
            "太烂", "无语", "欺骗", "坑人"};
    private static final String[] NEGATIVE = {
            "太慢", "太差", "不满意", "失望", "烂", "很差", "催", "急死", "怎么还没", "搞什么",
            "退款", "投诉", "态度差", "没人管", "拖", "慢死"};
    private static final String[] POSITIVE = {
            "谢谢", "满意", "好的", "不错", "赞", "感谢", "太好了", "非常好", "点赞", "麻烦了"};

    public EmotionResult detect(String text) {
        if (text == null || text.isBlank()) {
            return new EmotionResult(Sentiment.NEUTRAL, 0.1);
        }
        String t = text.toLowerCase();
        if (containsAny(t, STRONG_NEGATIVE)) {
            return new EmotionResult(Sentiment.NEGATIVE, 0.9);
        }
        if (containsAny(t, NEGATIVE)) {
            return new EmotionResult(Sentiment.NEGATIVE, 0.65);
        }
        if (containsAny(t, POSITIVE)) {
            return new EmotionResult(Sentiment.POSITIVE, 0.0);
        }
        return new EmotionResult(Sentiment.NEUTRAL, 0.1);
    }

    private boolean containsAny(String text, String[] kws) {
        for (String kw : kws) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }
}
