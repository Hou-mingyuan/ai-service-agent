package com.portfolio.csagent.llm;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class LlmChatRequest {

    private List<ChatMsg> messages = new ArrayList<>();
    private List<ToolSpec> tools = new ArrayList<>();
    /** 是否允许模型发起工具调用 */
    private boolean allowTools = true;

    public LlmChatRequest() {
    }

    public LlmChatRequest(List<ChatMsg> messages, List<ToolSpec> tools) {
        this.messages = messages;
        this.tools = tools;
    }
}
