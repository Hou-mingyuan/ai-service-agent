package com.portfolio.csagent.agent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.csagent.agent.tool.ToolExecutionContext;
import com.portfolio.csagent.agent.tool.ToolRegistry;
import com.portfolio.csagent.agent.tool.ToolResult;
import com.portfolio.csagent.common.BizException;
import com.portfolio.csagent.config.AppProperties;
import com.portfolio.csagent.entity.Conversation;
import com.portfolio.csagent.entity.Message;
import com.portfolio.csagent.entity.Ticket;
import com.portfolio.csagent.llm.ChatMsg;
import com.portfolio.csagent.llm.LlmChatRequest;
import com.portfolio.csagent.llm.LlmChatResult;
import com.portfolio.csagent.llm.LlmClient;
import com.portfolio.csagent.llm.ToolCall;
import com.portfolio.csagent.service.AuditService;
import com.portfolio.csagent.service.ConversationService;
import com.portfolio.csagent.service.HandoffService;
import com.portfolio.csagent.service.KnowledgeSearchResult;
import com.portfolio.csagent.service.KnowledgeService;
import org.springframework.stereotype.Service;

@Service
public class AgentOrchestrator {
    private static final int MAX_TOOL_CALLS_PER_TURN = 4;

    private final LlmClient llm;
    private final ToolRegistry toolRegistry;
    private final IntentService intentService;
    private final EmotionService emotionService;
    private final PromptSafetyService promptSafetyService;
    private final KnowledgeService knowledgeService;
    private final ConversationService conversationService;
    private final HandoffService handoffService;
    private final AuditService auditService;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public AgentOrchestrator(LlmClient llm, ToolRegistry toolRegistry, IntentService intentService,
                             EmotionService emotionService, PromptSafetyService promptSafetyService,
                             KnowledgeService knowledgeService, ConversationService conversationService,
                             HandoffService handoffService, AuditService auditService,
                             AppProperties properties, ObjectMapper objectMapper) {
        this.llm = llm;
        this.toolRegistry = toolRegistry;
        this.intentService = intentService;
        this.emotionService = emotionService;
        this.promptSafetyService = promptSafetyService;
        this.knowledgeService = knowledgeService;
        this.conversationService = conversationService;
        this.handoffService = handoffService;
        this.auditService = auditService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public AgentReply handle(Conversation conversation, String userText,
                             AgentRequestContext context, AgentEventSink sink) {
        ensureActive(context);
        IntentResult intentResult = intentService.classify(userText);
        Intent intent = intentResult.intent();
        EmotionResult emotion = emotionService.detect(userText);
        conversationService.updateClassification(context.userMessageId(), intentResult, emotion);
        conversationService.updateMeta(conversation, intent.name(), emotion.sentiment().name());

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("conversationId", conversation.getId());
        meta.put("intent", intent.name());
        meta.put("intentLabel", intent.label());
        meta.put("intentConfidence", intentResult.confidence());
        meta.put("classificationSource", intentResult.source());
        meta.put("sentiment", emotion.sentiment().name());
        meta.put("sentimentLabel", emotion.sentiment().label());
        meta.put("sentimentScore", emotion.score());
        meta.put("sentimentConfidence", emotion.confidence());
        sink.event("meta", meta);

        PromptSafetyService.SafetyResult safety = promptSafetyService.inspect(userText);
        if (!safety.allowed()) {
            auditService.record(context.actor(), "PROMPT_BLOCKED", "CONVERSATION", conversation.getId(),
                    "DENIED", context.clientRequestId(), Map.of("code", safety.code(), "marker", safety.marker()));
            String answer = "这条消息包含试图改变系统规则或获取受保护信息的内容，已被安全策略拦截。"
                    + "如需业务帮助，请直接描述订单、物流、保单或售后问题。";
            streamText(answer, sink, context);
            return new AgentReply(answer, intent, intentResult.confidence(), emotion, null,
                    false, List.of());
        }

        boolean explicitHandoff = intent == Intent.HUMAN_AGENT || intent == Intent.TICKET_CREATE;
        boolean negativeHandoff = emotion.isNegative()
                && emotion.score() >= properties.getAgent().getNegativeEscalateThreshold();
        boolean lowConfidence = intentResult.confidence() < properties.getAgent().getLowConfidenceThreshold();
        if (explicitHandoff || negativeHandoff || lowConfidence) {
            String reason = explicitHandoff ? "客户主动要求人工或创建工单"
                    : negativeHandoff ? "检测到明显负面情绪" : "意图置信度过低";
            return handoff(conversation, userText, intentResult, emotion, reason, context, sink);
        }
        return autoAnswer(conversation, userText, intentResult, emotion, context, sink);
    }

    private AgentReply autoAnswer(Conversation conversation, String userText, IntentResult intentResult,
                                  EmotionResult emotion, AgentRequestContext context, AgentEventSink sink) {
        List<KnowledgeSearchResult> citations = knowledgeService.search(context.actor().tenantId(), userText,
                properties.getAgent().getKnowledgeTopK(), properties.getAgent().getKnowledgeMinScore());
        if (intentResult.intent() == Intent.FAQ && citations.isEmpty()) {
            return handoff(conversation, userText, intentResult, emotion,
                    "知识库没有达到阈值的可靠答案", context, sink);
        }

        List<ChatMsg> messages = new ArrayList<>();
        messages.add(ChatMsg.system(properties.getAgent().getSystemPrompt()));
        if (!citations.isEmpty()) {
            sink.event("citations", Map.of("items", citations, "dataSource", "local-lexical"));
            StringBuilder knowledge = new StringBuilder("KNOWLEDGE_CONTEXT:\n");
            for (KnowledgeSearchResult citation : citations) {
                knowledge.append('[').append(citation.citation()).append("] ")
                        .append(citation.title()).append(" | ")
                        .append(citation.sourceUri() == null ? "internal" : citation.sourceUri())
                        .append("\n").append(citation.excerpt()).append("\n");
            }
            messages.add(ChatMsg.system(knowledge.toString()));
        }
        for (Message message : conversationService.history(conversation.getId(),
                properties.getAgent().getHistoryTurns() * 2)) {
            if ("user".equals(message.getRole())) {
                messages.add(ChatMsg.user(message.getContent()));
            } else if (("assistant".equals(message.getRole()) || "agent".equals(message.getRole()))
                    && message.getContent() != null) {
                messages.add(ChatMsg.assistant(message.getContent()));
            }
        }

        Set<String> toolSignatures = new HashSet<>();
        StringBuilder answer = new StringBuilder();
        int toolCalls = 0;
        for (int round = 0; round < properties.getLlm().getMaxToolRounds(); round++) {
            ensureActive(context);
            LlmChatRequest request = new LlmChatRequest(new ArrayList<>(messages), toolRegistry.specs());
            LlmChatResult plan = llm.chat(request);
            if (!plan.hasToolCalls()) {
                if (plan.content() != null && !plan.content().isBlank()) {
                    streamText(plan.content(), sink, context);
                    answer.append(plan.content());
                } else {
                    request.setAllowTools(false);
                    llm.chatStream(request, token -> {
                        ensureActive(context);
                        answer.append(token);
                        sink.event("token", Map.of("text", token));
                    });
                }
                if (answer.isEmpty()) {
                    String fallback = "当前没有生成可靠答复，已停止自动处理。";
                    streamText(fallback, sink, context);
                    answer.append(fallback);
                }
                return new AgentReply(answer.toString(), intentResult.intent(), intentResult.confidence(),
                        emotion, null, false, citations);
            }

            if (toolCalls + plan.toolCalls().size() > MAX_TOOL_CALLS_PER_TURN) {
                return guardHandoff(conversation, userText, intentResult, emotion,
                        "单轮工具调用超过上限", context, sink);
            }
            messages.add(ChatMsg.assistantToolCalls(plan.toolCalls()));
            boolean pendingConfirmation = false;
            for (ToolCall call : plan.toolCalls()) {
                ensureActive(context);
                Map<String, Object> args = parseArgs(call.arguments());
                if (args == null) {
                    auditService.record(context.actor(), "MODEL_TOOL_ARGUMENT_INVALID", "CONVERSATION",
                            conversation.getId(), "DENIED", context.clientRequestId(), Map.of("tool", call.name()));
                    ToolResult invalid = ToolResult.fail("工具参数格式无效，未执行任何业务操作。")
                            .persisted(null, "none", false);
                    emitToolResult(call.name(), invalid, sink);
                    messages.add(ChatMsg.tool(call.id(), call.name(), invalid.summary()));
                    continue;
                }
                String signature = call.name() + "|" + toJson(new TreeMap<>(args));
                if (!toolSignatures.add(signature)) {
                    return guardHandoff(conversation, userText, intentResult, emotion,
                            "检测到重复工具调用循环", context, sink);
                }
                toolCalls++;
                sink.event("tool_call", Map.of("name", call.name(), "arguments", args,
                        "round", round + 1));

                ToolExecutionContext toolContext = new ToolExecutionContext(context.actor(), conversation,
                        context.clientRequestId(), context.requestId(), null);
                ToolResult result;
                try {
                    result = toolRegistry.execute(call.name(), args, toolContext);
                } catch (BizException exception) {
                    result = new ToolResult(false, exception.getMessage(), null, "REJECTED", null,
                            "none", false);
                }
                emitToolResult(call.name(), result, sink);
                conversationService.addMessage(conversation.getId(), "tool", result.summary(),
                        intentResult.intent().name(), null, null, call.name(), toJson(args), toJson(result.data()));
                messages.add(ChatMsg.tool(call.id(), call.name(), result.summary()));
                if ("PENDING_CONFIRMATION".equals(result.status())) {
                    pendingConfirmation = true;
                    sink.event("confirmation_required", Map.of(
                            "executionId", result.executionId(), "tool", call.name(),
                            "summary", result.summary(), "arguments", args,
                            "dataSource", result.dataSource()));
                    if (!answer.isEmpty()) {
                        answer.append('\n');
                    }
                    answer.append(result.summary());
                }
            }
            if (pendingConfirmation) {
                streamText(answer.toString(), sink, context);
                return new AgentReply(answer.toString(), intentResult.intent(), intentResult.confidence(),
                        emotion, null, false, citations);
            }
        }
        return guardHandoff(conversation, userText, intentResult, emotion,
                "达到最大工具轮次", context, sink);
    }

    private AgentReply guardHandoff(Conversation conversation, String userText, IntentResult intentResult,
                                    EmotionResult emotion, String reason, AgentRequestContext context,
                                    AgentEventSink sink) {
        auditService.record(context.actor(), "AGENT_GUARD_TRIGGERED", "CONVERSATION", conversation.getId(),
                "HANDOFF", context.clientRequestId(), Map.of("reason", reason));
        return handoff(conversation, userText, intentResult, emotion, reason, context, sink);
    }

    private AgentReply handoff(Conversation conversation, String userText, IntentResult intentResult,
                               EmotionResult emotion, String reason, AgentRequestContext context,
                               AgentEventSink sink) {
        String category = switch (intentResult.intent()) {
            case COMPLAINT -> "COMPLAINT";
            case ORDER_QUERY -> "ORDER";
            case LOGISTICS_QUERY -> "LOGISTICS";
            case POLICY_QUERY -> "POLICY";
            default -> "OTHER";
        };
        String priority = emotion.score() >= 0.9 ? "URGENT" : emotion.isNegative() ? "HIGH" : "MEDIUM";
        String title = intentResult.intent() == Intent.HUMAN_AGENT ? "客户请求人工客服"
                : intentResult.intent() == Intent.TICKET_CREATE ? "客户请求创建工单"
                : "自动升级人工处理";
        HandoffService.InitiateResult initiated = handoffService.initiate(conversation, reason, category,
                title, userText, priority, context.clientRequestId());
        Ticket ticket = initiated.ticket();
        sink.event("handoff", Map.of("ticketNo", ticket.getTicketNo(), "ticketId", ticket.getId(),
                "priority", ticket.getPriority(), "reason", reason, "replayed", initiated.replayed()));
        String answer = "已将会话转入人工队列，并创建工单 " + ticket.getTicketNo()
                + "。机器人现已暂停，坐席认领后会在本会话中直接回复。";
        streamText(answer, sink, context);
        return new AgentReply(answer, intentResult.intent(), intentResult.confidence(), emotion,
                ticket, true, List.of());
    }

    private void emitToolResult(String name, ToolResult result, AgentEventSink sink) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("name", name);
        event.put("ok", result.ok());
        event.put("summary", result.summary());
        event.put("data", result.data());
        event.put("status", result.status());
        event.put("executionId", result.executionId());
        event.put("dataSource", result.dataSource());
        event.put("replayed", result.replayed());
        sink.event("tool_result", event);
    }

    private void streamText(String text, AgentEventSink sink, AgentRequestContext context) {
        int step = 8;
        for (int i = 0; i < text.length(); i += step) {
            ensureActive(context);
            sink.event("token", Map.of("text", text.substring(i, Math.min(text.length(), i + step))));
        }
    }

    private void ensureActive(AgentRequestContext context) {
        if (context.isCancelled()) {
            throw new CancellationException("SSE client disconnected");
        }
    }

    private Map<String, Object> parseArgs(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (Exception exception) {
            return null;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception exception) {
            return "{}";
        }
    }
}
