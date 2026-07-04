package com.portfolio.csagent.agent;

import com.portfolio.csagent.entity.Ticket;

/** 一轮 Agent 处理的结果汇总。 */
public record AgentReply(
        String answer,
        Intent intent,
        EmotionResult emotion,
        Ticket ticket,
        boolean handoff) {
}
