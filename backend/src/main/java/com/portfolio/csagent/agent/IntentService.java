package com.portfolio.csagent.agent;

import org.springframework.stereotype.Service;

/**
 * 意图识别：轻量规则/关键词模型，零依赖、确定可测。
 * 生产可平滑替换为 LLM few-shot 或独立分类模型，接口保持不变。
 */
@Service
public class IntentService {

    public Intent detect(String text) {
        if (text == null || text.isBlank()) {
            return Intent.OTHER;
        }
        String t = text.toLowerCase();

        if (containsAny(t, "转人工", "人工客服", "真人", "找人工", "人工坐席", "接人工")) {
            return Intent.HUMAN_AGENT;
        }
        if (containsAny(t, "投诉", "差评", "太差", "垃圾", "曝光", "315", "骗子", "恶心", "气死", "太烂")) {
            return Intent.COMPLAINT;
        }
        if (containsAny(t, "改期", "改签", "重新预约", "换个时间", "换时间", "预约变更", "改约", "延期")) {
            return Intent.RESCHEDULE;
        }
        if (containsAny(t, "工单") && containsAny(t, "查", "进度", "状态", "查询", "处理", "怎么样了")) {
            return Intent.TICKET_QUERY;
        }
        if (containsAny(t, "创建工单", "提工单", "开工单", "登记问题", "记录问题", "帮我登记")) {
            return Intent.TICKET_CREATE;
        }
        if (containsAny(t, "物流", "快递", "发货", "到哪", "运单", "签收", "配送", "什么时候到", "几天到")) {
            return Intent.LOGISTICS_QUERY;
        }
        if (containsAny(t, "订单", "order", "下单", "买的", "购买记录")) {
            return Intent.ORDER_QUERY;
        }
        if (containsAny(t, "保单", "保险", "投保", "理赔", "续保", "保费", "退保", "缴费")) {
            return Intent.POLICY_QUERY;
        }
        if (containsAny(t, "你好", "您好", "hi", "hello", "在吗", "在么", "早上好", "下午好")) {
            return Intent.GREETING;
        }
        if (containsAny(t, "怎么", "如何", "为什么", "政策", "规则", "发票", "多久", "几天", "多少钱",
                "能不能", "可以吗", "是否")) {
            return Intent.FAQ;
        }
        return Intent.OTHER;
    }

    private boolean containsAny(String text, String... kws) {
        for (String kw : kws) {
            if (text.contains(kw.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
