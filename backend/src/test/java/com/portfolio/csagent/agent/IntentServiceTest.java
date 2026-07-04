package com.portfolio.csagent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IntentServiceTest {

    private final IntentService service = new IntentService();

    @Test
    @DisplayName("订单/物流/保单查询意图识别")
    void detectQueryIntents() {
        assertEquals(Intent.ORDER_QUERY, service.detect("帮我查订单123"));
        assertEquals(Intent.LOGISTICS_QUERY, service.detect("我的快递到哪了"));
        assertEquals(Intent.POLICY_QUERY, service.detect("查一下保单 PAI2024001"));
    }

    @Test
    @DisplayName("工单/改期意图识别")
    void detectTicketAndReschedule() {
        assertEquals(Intent.TICKET_QUERY, service.detect("查一下工单进度"));
        assertEquals(Intent.RESCHEDULE, service.detect("我想改期到下周"));
    }

    @Test
    @DisplayName("转人工与投诉优先于普通业务意图")
    void detectHumanAndComplaint() {
        assertEquals(Intent.HUMAN_AGENT, service.detect("请帮我转人工"));
        assertEquals(Intent.COMPLAINT, service.detect("你们太差了，我要投诉这个订单"));
    }

    @Test
    @DisplayName("打招呼 / FAQ / 兜底")
    void detectGreetingFaqOther() {
        assertEquals(Intent.GREETING, service.detect("你好"));
        assertEquals(Intent.FAQ, service.detect("退货政策是怎样的"));
        assertEquals(Intent.OTHER, service.detect("嗯嗯"));
    }
}
