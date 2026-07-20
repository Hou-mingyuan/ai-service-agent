package com.portfolio.csagent.agent;

import org.springframework.stereotype.Service;

/**
 * 意图识别：轻量规则/关键词模型，零依赖、确定可测。
 * 生产可平滑替换为 LLM few-shot 或独立分类模型，接口保持不变。
 */
@Service
public class IntentService {

    public Intent detect(String text) {
        return classify(text).intent();
    }

    public IntentResult classify(String text) {
        if (text == null || text.isBlank()) {
            return result(Intent.OTHER, 0.1);
        }
        String t = text.toLowerCase();

        if (containsAny(t, "转人工", "人工客服", "真人", "找人工", "人工坐席", "接人工")) {
            return result(Intent.HUMAN_AGENT, 0.99);
        }
        if (containsAny(t, "投诉", "差评", "太差", "垃圾", "曝光", "315", "骗子", "恶心", "气死", "太烂")) {
            return result(Intent.COMPLAINT, 0.96);
        }
        if (containsAny(t, "改期", "改签", "重新预约", "换个时间", "换时间", "预约变更", "改约", "延期")) {
            return result(Intent.RESCHEDULE, 0.9);
        }
        if (containsAny(t, "工单") && containsAny(t, "查", "进度", "状态", "查询", "处理", "怎么样了")) {
            return result(Intent.TICKET_QUERY, 0.9);
        }
        if (containsAny(t, "创建工单", "提工单", "开工单", "登记问题", "记录问题", "帮我登记")) {
            return result(Intent.TICKET_CREATE, 0.9);
        }
        if (containsAny(t, "物流", "快递", "发货", "到哪", "运单", "签收", "配送", "什么时候到", "几天到")) {
            return result(Intent.LOGISTICS_QUERY, 0.9);
        }
        if (containsAny(t, "订单", "order", "下单", "买的", "购买记录")) {
            return result(Intent.ORDER_QUERY, 0.9);
        }
        if (containsAny(t, "保单", "保险", "投保", "理赔", "续保", "保费", "退保", "缴费")) {
            return result(Intent.POLICY_QUERY, 0.9);
        }
        if (containsAny(t, "你好", "您好", "hi", "hello", "在吗", "在么", "早上好", "下午好")) {
            return result(Intent.GREETING, 0.98);
        }
        if (containsAny(t, "怎么", "如何", "为什么", "什么", "条件", "要求", "流程", "说明", "指南",
                "政策", "规则", "发票", "多久", "几天", "多少钱", "能不能", "可以吗", "是否")) {
            return result(Intent.FAQ, 0.72);
        }
        return result(Intent.OTHER, 0.3);
    }

    private boolean containsAny(String text, String... kws) {
        for (String kw : kws) {
            if (text.contains(kw.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private IntentResult result(Intent intent, double confidence) {
        return new IntentResult(intent, confidence, "rules");
    }
}
