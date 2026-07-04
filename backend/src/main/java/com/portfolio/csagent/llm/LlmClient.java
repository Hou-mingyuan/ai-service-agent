package com.portfolio.csagent.llm;

import java.util.function.Consumer;

/** 大模型客户端抽象：可插拔多供应方（OpenAI 兼容 / 离线 Mock）。 */
public interface LlmClient {

    /** 供应方标识：openai / mock */
    String provider();

    /** 非流式对话，支持 Function Calling（返回工具调用或文本）。 */
    LlmChatResult chat(LlmChatRequest request);

    /** 流式对话：逐 token 回调，用于生成最终答复。 */
    void chatStream(LlmChatRequest request, Consumer<String> onToken);
}
