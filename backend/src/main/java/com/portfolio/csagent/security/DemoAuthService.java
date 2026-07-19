package com.portfolio.csagent.security;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

/** Phase 1 演示账号（单租户骨架）。生产环境应替换为 DB 用户表。 */
@Service
public class DemoAuthService {

    private static final Map<String, DemoUser> USERS = Map.of(
            "agent", new DemoUser("agent", "agent123", Role.AGENT),
            "supervisor", new DemoUser("supervisor", "super123", Role.SUPERVISOR),
            "admin", new DemoUser("admin", "admin123", Role.ADMIN));

    public Optional<DemoUser> authenticate(String username, String password) {
        DemoUser user = USERS.get(username);
        if (user != null && user.password().equals(password)) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public record DemoUser(String username, String password, Role role) {
    }
}
