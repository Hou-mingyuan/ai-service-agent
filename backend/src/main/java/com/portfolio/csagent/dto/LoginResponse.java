package com.portfolio.csagent.dto;

import java.util.Set;

import com.portfolio.csagent.security.AuthenticatedUser;

public record LoginResponse(
        String accessToken,
        String tokenType,
        String username,
        String displayName,
        String role,
        Set<String> permissions,
        long expiresInMinutes) {
    public static LoginResponse from(AuthenticatedUser user, String token, long expiresInMinutes) {
        return new LoginResponse(token, "Bearer", user.username(), user.displayName(), user.role().getId(),
                user.role().getPermissions(), expiresInMinutes);
    }
}
