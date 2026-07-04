package com.portfolio.csagent.agent;

/** 客服意图分类。 */
public enum Intent {

    GREETING("打招呼"),
    ORDER_QUERY("订单查询"),
    LOGISTICS_QUERY("物流查询"),
    POLICY_QUERY("保单查询"),
    TICKET_CREATE("创建工单"),
    TICKET_QUERY("工单查询"),
    RESCHEDULE("预约改期"),
    COMPLAINT("投诉抱怨"),
    HUMAN_AGENT("转接人工"),
    FAQ("常见问题"),
    OTHER("其它");

    private final String label;

    Intent(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
