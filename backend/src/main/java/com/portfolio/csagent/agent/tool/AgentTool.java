package com.portfolio.csagent.agent.tool;

import java.util.Map;

import com.portfolio.csagent.llm.ToolSpec;

/** Agent 可调用的业务工具。实现类声明 Schema 并执行真实业务查询/操作。 */
public interface AgentTool {

    /** 工具名（唯一，英文） */
    String name();

    /** 传给 LLM 的 Function 声明 */
    ToolSpec spec();

    /** 执行工具。args 为已解析的参数（含编排层注入的 conversation_id / customer）。 */
    ToolResult execute(Map<String, Object> args);
}
