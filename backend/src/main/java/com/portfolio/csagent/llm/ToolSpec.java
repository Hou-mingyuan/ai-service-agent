package com.portfolio.csagent.llm;

import java.util.Map;

/**
 * 工具（function）声明，用于传给支持 Function Calling 的模型。
 *
 * @param name        工具名（英文，唯一）
 * @param description 工具用途描述（供模型理解何时调用）
 * @param parameters  JSON Schema 形式的参数定义
 */
public record ToolSpec(String name, String description, Map<String, Object> parameters) {
}
