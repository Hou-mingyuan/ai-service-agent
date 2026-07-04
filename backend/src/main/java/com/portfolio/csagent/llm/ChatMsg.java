package com.portfolio.csagent.llm;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 传给 LLM 的一条消息，兼容 OpenAI chat/completions 协议。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMsg {

    /** system / user / assistant / tool */
    private String role;
    private String content;
    /** tool 消息：对应的工具名 */
    private String name;
    /** tool 消息：回应的 tool_call id */
    private String toolCallId;
    /** assistant 消息：模型发起的工具调用 */
    private List<ToolCall> toolCalls;

    public static ChatMsg system(String content) {
        return new ChatMsg("system", content, null, null, null);
    }

    public static ChatMsg user(String content) {
        return new ChatMsg("user", content, null, null, null);
    }

    public static ChatMsg assistant(String content) {
        return new ChatMsg("assistant", content, null, null, null);
    }

    public static ChatMsg assistantToolCalls(List<ToolCall> toolCalls) {
        return new ChatMsg("assistant", null, null, null, toolCalls);
    }

    public static ChatMsg tool(String toolCallId, String name, String content) {
        return new ChatMsg("tool", content, name, toolCallId, null);
    }
}
