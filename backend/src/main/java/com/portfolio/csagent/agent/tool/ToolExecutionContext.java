package com.portfolio.csagent.agent.tool;

import com.portfolio.csagent.entity.Conversation;
import com.portfolio.csagent.security.AuthenticatedUser;

public record ToolExecutionContext(
        AuthenticatedUser actor,
        Conversation conversation,
        String clientRequestId,
        String requestId,
        String executionKey) {
}
