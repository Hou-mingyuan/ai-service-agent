package com.portfolio.csagent.agent.tool;

import java.util.Map;

import com.portfolio.csagent.llm.ToolSpec;
import com.portfolio.csagent.security.Permission;

public interface AgentTool {
    String name();

    ToolSpec spec();

    ToolResult execute(ToolExecutionContext context, Map<String, Object> args);

    default boolean sensitive() {
        return false;
    }

    default String requiredPermission() {
        return Permission.TOOL_READ;
    }

    default String adapterSource() {
        return "internal";
    }

    default int timeoutSeconds() {
        return 8;
    }

    default void validate(ToolExecutionContext context, Map<String, Object> args) {
        // Tool-specific resource checks run before a sensitive confirmation is created.
    }

    default String confirmationSummary(ToolExecutionContext context, Map<String, Object> args) {
        return "该操作会修改业务数据，请确认后继续。";
    }
}
