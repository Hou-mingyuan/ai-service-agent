package com.portfolio.csagent.agent;

/** Agent 运行过程中的事件回调（SSE 推送 / 测试收集）。 */
@FunctionalInterface
public interface AgentEventSink {

    void event(String type, Object data);

    AgentEventSink NOOP = (type, data) -> {
    };
}
