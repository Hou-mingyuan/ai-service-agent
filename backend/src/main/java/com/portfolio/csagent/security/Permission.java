package com.portfolio.csagent.security;

/** RBAC 权限常量（资源:动作），与 {@link Role} 及 Spring Security authority 对齐。 */
public final class Permission {

    public static final String CHAT_SEND = "chat:send";
    public static final String FEEDBACK_SUBMIT = "feedback:submit";
    public static final String TICKET_READ = "ticket:read";
    public static final String TICKET_TRANSITION = "ticket:transition";
    public static final String TICKET_ASSIGN = "ticket:assign";
    public static final String TICKET_ESCALATE = "ticket:escalate";
    public static final String DASHBOARD_READ = "dashboard:read";
    public static final String WS_AGENT = "ws:agent";
    public static final String CATALOG_WRITE = "catalog:write";
    public static final String CONFIG_WRITE = "config:write";

    private Permission() {
    }
}
