package com.portfolio.csagent.agent.tool;

/** 工具名常量，供工具实现与离线 Mock 路由共享。 */
public final class ToolNames {

    private ToolNames() {
    }

    public static final String QUERY_ORDER = "query_order";
    public static final String QUERY_LOGISTICS = "query_logistics";
    public static final String QUERY_POLICY = "query_policy";
    public static final String CREATE_TICKET = "create_ticket";
    public static final String QUERY_TICKET = "query_ticket";
    public static final String RESCHEDULE = "reschedule_appointment";
}
