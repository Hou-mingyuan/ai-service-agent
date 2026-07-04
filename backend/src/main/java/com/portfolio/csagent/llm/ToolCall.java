package com.portfolio.csagent.llm;

/** 模型请求的一次工具调用。arguments 为 JSON 字符串。 */
public record ToolCall(String id, String name, String arguments) {
}
