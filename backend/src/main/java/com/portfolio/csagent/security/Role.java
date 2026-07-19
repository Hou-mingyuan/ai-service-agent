package com.portfolio.csagent.security;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * 单租户角色枚举。{@link #getAuthorities()} 返回 Spring Security 可用的 authority 集合。
 */
public enum Role {

    VISITOR("visitor", Permission.CHAT_SEND, Permission.FEEDBACK_SUBMIT),
    AGENT("agent",
            Permission.TICKET_READ,
            Permission.TICKET_TRANSITION,
            Permission.WS_AGENT),
    SUPERVISOR("supervisor",
            Permission.TICKET_READ,
            Permission.TICKET_TRANSITION,
            Permission.TICKET_ASSIGN,
            Permission.TICKET_ESCALATE,
            Permission.DASHBOARD_READ,
            Permission.WS_AGENT),
    ADMIN("admin",
            Permission.CHAT_SEND,
            Permission.FEEDBACK_SUBMIT,
            Permission.TICKET_READ,
            Permission.TICKET_TRANSITION,
            Permission.TICKET_ASSIGN,
            Permission.TICKET_ESCALATE,
            Permission.DASHBOARD_READ,
            Permission.WS_AGENT,
            Permission.CATALOG_WRITE,
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
        return permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toUnmodifiableSet());
    }

    public static Role fromId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (Role role : values()) {
            if (role.id.equalsIgnoreCase(id)) {
                return role;
            }
        }
        return null;
    }

    /** agent 及以上角色可访问坐席 API。 */
    public boolean isAgentOrAbove() {
        return EnumSet.of(AGENT, SUPERVISOR, ADMIN).contains(this);
    }

    /** supervisor 及以上角色可访问运营看板。 */
    public boolean isSupervisorOrAbove() {
        return EnumSet.of(SUPERVISOR, ADMIN).contains(this);
    }
}
