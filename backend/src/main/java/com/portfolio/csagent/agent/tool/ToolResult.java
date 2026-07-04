package com.portfolio.csagent.agent.tool;

/**
 * 工具执行结果。
 *
 * @param ok      是否成功命中数据
 * @param summary 面向用户/模型的自然语言小结（最终答复的主要依据）
 * @param data    结构化数据（前端可用于展示卡片）
 */
public record ToolResult(boolean ok, String summary, Object data) {

    public static ToolResult ok(String summary, Object data) {
        return new ToolResult(true, summary, data);
    }

    public static ToolResult fail(String summary) {
        return new ToolResult(false, summary, null);
    }
}
