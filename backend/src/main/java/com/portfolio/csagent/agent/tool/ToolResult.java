package com.portfolio.csagent.agent.tool;

public record ToolResult(
        boolean ok,
        String summary,
        Object data,
        String status,
        Long executionId,
        String dataSource,
        boolean replayed) {

    public static ToolResult ok(String summary, Object data) {
        return new ToolResult(true, summary, data, "COMPLETED", null, null, false);
    }

    public static ToolResult fail(String summary) {
        return new ToolResult(false, summary, null, "COMPLETED", null, null, false);
    }

    public static ToolResult confirmation(String summary, Object data) {
        return new ToolResult(true, summary, data, "PENDING_CONFIRMATION", null, null, false);
    }

    public ToolResult persisted(Long id, String source, boolean isReplay) {
        return new ToolResult(ok, summary, data, status, id, source, isReplay);
    }
}
