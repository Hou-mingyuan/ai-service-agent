package com.portfolio.csagent.security;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

public enum Role {
    CUSTOMER("customer",
            Permission.CHAT_SEND,
            Permission.FEEDBACK_SUBMIT,
            Permission.CONVERSATION_SELF,
            Permission.TOOL_READ,
            Permission.TOOL_SENSITIVE,
            Permission.WS_EVENTS),
    AGENT("agent",
            Permission.CONVERSATION_QUEUE,
            Permission.MESSAGE_REPLY,
            Permission.TICKET_READ,
            Permission.TICKET_TRANSITION,
            Permission.TOOL_READ,
            Permission.WS_EVENTS),
    SUPERVISOR("supervisor",
            Permission.CONVERSATION_QUEUE,
            Permission.CONVERSATION_ALL,
            Permission.MESSAGE_REPLY,
            Permission.TICKET_READ,
            Permission.TICKET_TRANSITION,
            Permission.TICKET_ASSIGN,
            Permission.TICKET_ESCALATE,
            Permission.TOOL_READ,
            Permission.KNOWLEDGE_READ,
            Permission.DASHBOARD_READ,
            Permission.AUDIT_READ,
            Permission.WS_EVENTS),
    ADMIN("admin",
            Permission.CHAT_SEND,
            Permission.FEEDBACK_SUBMIT,
            Permission.CONVERSATION_SELF,
            Permission.CONVERSATION_QUEUE,
            Permission.CONVERSATION_ALL,
            Permission.MESSAGE_REPLY,
            Permission.TICKET_READ,
            Permission.TICKET_TRANSITION,
            Permission.TICKET_ASSIGN,
            Permission.TICKET_ESCALATE,
            Permission.TOOL_READ,
            Permission.TOOL_SENSITIVE,
            Permission.KNOWLEDGE_READ,
            Permission.KNOWLEDGE_WRITE,
            Permission.DASHBOARD_READ,
            Permission.AUDIT_READ,
            Permission.WS_EVENTS,
            Permission.CONFIG_WRITE);

    private final String id;
    private final Set<String> permissions;

    Role(String id, String... permissions) {
        this.id = id;
        this.permissions = Collections.unmodifiableSet(Arrays.stream(permissions).collect(Collectors.toSet()));
    }

    public String getId() {
        return id;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public Set<SimpleGrantedAuthority> getAuthorities() {
        return permissions.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toUnmodifiableSet());
    }

    public static Role fromId(String id) {
        if (id == null) {
            return null;
        }
        return Arrays.stream(values()).filter(role -> role.id.equalsIgnoreCase(id)).findFirst().orElse(null);
    }

    public boolean isStaff() {
        return this != CUSTOMER;
    }

    public boolean isSupervisorOrAbove() {
        return this == SUPERVISOR || this == ADMIN;
    }
}
