package com.portfolio.csagent.agent;

import java.util.concurrent.atomic.AtomicBoolean;

import com.portfolio.csagent.security.AuthenticatedUser;

public record AgentRequestContext(
        AuthenticatedUser actor,
        String clientRequestId,
        String requestId,
        Long userMessageId,
        AtomicBoolean cancelled) {
    public boolean isCancelled() {
        return cancelled != null && cancelled.get();
    }
}
