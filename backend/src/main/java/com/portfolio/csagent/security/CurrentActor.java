package com.portfolio.csagent.security;

import java.util.Optional;

import com.portfolio.csagent.common.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentActor {
    public AuthenticatedUser require() {
        return optional().orElseThrow(() -> new BizException(401, "请先登录"));
    }

    public Optional<AuthenticatedUser> optional() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    public boolean has(String permission) {
        return require().role().getPermissions().contains(permission);
    }
}
