package com.portfolio.csagent.security;

public record AuthenticatedUser(
        Long userId,
        String tenantId,
        String username,
        String displayName,
        Role role) {
}
