package com.portfolio.csagent.security;

/** Server-enforced resource:action authorities. */
public final class Permission {
    public static final String CHAT_SEND = "chat:send";
    public static final String FEEDBACK_SUBMIT = "feedback:submit";
    public static final String CONVERSATION_SELF = "conversation:self";
    public static final String CONVERSATION_QUEUE = "conversation:queue";
    public static final String CONVERSATION_ALL = "conversation:all";
    public static final String MESSAGE_REPLY = "message:reply";
    public static final String TICKET_READ = "ticket:read";
    public static final String TICKET_TRANSITION = "ticket:transition";
    public static final String TICKET_ASSIGN = "ticket:assign";
    public static final String TICKET_ESCALATE = "ticket:escalate";
    public static final String TOOL_READ = "tool:read";
    public static final String TOOL_SENSITIVE = "tool:sensitive";
    public static final String KNOWLEDGE_READ = "knowledge:read";
    public static final String KNOWLEDGE_WRITE = "knowledge:write";
    public static final String DASHBOARD_READ = "dashboard:read";
    public static final String AUDIT_READ = "audit:read";
    public static final String WS_EVENTS = "ws:events";
    public static final String CONFIG_WRITE = "config:write";

    private Permission() {
    }
}
