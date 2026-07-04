package com.portfolio.csagent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmotionServiceTest {

    private final EmotionService service = new EmotionService();

    @Test
    @DisplayName("强负面词触发高负面分数")
    void strongNegative() {
        EmotionResult r = service.detect("你们太差了，我要投诉！");
        assertEquals(Sentiment.NEGATIVE, r.sentiment());
        assertTrue(r.score() >= 0.6, "投诉类应达到升级阈值");
    }

    @Test
    @DisplayName("一般负面词判为负面")
    void mildNegative() {
        EmotionResult r = service.detect("怎么还没发货，太慢了");
        assertEquals(Sentiment.NEGATIVE, r.sentiment());
    }

    @Test
    @DisplayName("正面与中性识别")
    void positiveAndNeutral() {
        assertEquals(Sentiment.POSITIVE, service.detect("谢谢，非常满意").sentiment());
        assertEquals(Sentiment.NEUTRAL, service.detect("帮我查订单123").sentiment());
    }
}
