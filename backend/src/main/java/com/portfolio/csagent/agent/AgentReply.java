package com.portfolio.csagent.agent;

import java.util.List;

import com.portfolio.csagent.entity.Ticket;
import com.portfolio.csagent.service.KnowledgeSearchResult;

public record AgentReply(
        String answer,
        Intent intent,
        double intentConfidence,
        EmotionResult emotion,
        Ticket ticket,
        boolean handoff,
        List<KnowledgeSearchResult> citations) {
}
