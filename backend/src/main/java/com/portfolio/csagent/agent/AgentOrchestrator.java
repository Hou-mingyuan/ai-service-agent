package com.portfolio.csagent.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.csagent.agent.tool.ToolRegistry;
import com.portfolio.csagent.agent.tool.ToolResult;
import com.portfolio.csagent.config.AppProperties;
import com.portfolio.csagent.entity.Conversation;
import com.portfolio.csagent.entity.Faq;
import com.portfolio.csagent.entity.Message;
import com.portfolio.csagent.entity.Ticket;
import com.portfolio.csagent.llm.ChatMsg;
import com.portfolio.csagent.llm.LlmChatRequest;
import com.portfolio.csagent.llm.LlmChatResult;
import com.portfolio.csagent.llm.LlmClient;
import com.portfolio.csagent.llm.ToolCall;
import com.portfolio.csagent.service.ConversationService;
import com.portfolio.csagent.service.FaqService;
import com.portfolio.csagent.service.TicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Agent 编排核心：意图识别 → 情绪识别 → 知识库召回 → 工具调用(Function Calling)
 * → 流式答复；负面情绪或显式请求时自动创建工单并转人工。
 */
@Service
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    private final LlmClient llm;
    private final ToolRegistry toolRegistry;
    private final IntentService intentService;
    private final EmotionService emotionService;
    private final FaqService faqService;
    private final ConversationService conversationService;
    private final TicketService ticketService;
    private final AppProperties props;
    private final ObjectMapper om;

    public AgentOrchestrator(LlmClient llm, ToolRegistry toolRegistry, IntentService intentService,
                             EmotionService emotionService, FaqService faqService,
                             ConversationService conversationService, TicketService ticketService,
                             AppProperties props, ObjectMapper om) {
        this.llm = llm;
        this.toolRegistry = toolRegistry;
        this.intentService = intentService;
        this.emotionService = emotionService;
        this.faqService = faqService;
        this.conversationService = conversationService;
        this.ticketService = ticketService;
        this.props = props;
        this.om = om;
    }

    public AgentReply handle(Conversation conversation, String userText, AgentEventSink sink) {
        Intent intent = intentService.detect(userText);
        EmotionResult emotion = emotionService.detect(userText);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("conversationId", conversation.getId());
        meta.put("intent", intent.name());
        meta.put("intentLabel", intent.label());
        meta.put("sentiment", emotion.sentiment().name());
        meta.put("sentimentLabel", emotion.sentiment().label());
        meta.put("score", emotion.score());
        sink.event("meta", meta);

        conversationService.updateMeta(conversation, intent.name(), emotion.sentiment().name());

        boolean needHuman = intent == Intent.HUMAN_AGENT
                || (emotion.isNegative()
                    && emotion.score() >= props.getAgent().getNegativeEscalateThreshold());
        if (needHuman) {
            return handoff(conversation, userText, intent, emotion, sink);
        }
        return autoAnswer(conversation, userText, intent, emotion, sink);
    }

    // ---------------- 转人工 ----------------

    private AgentReply handoff(Conversation conversation, String userText, Intent intent,
                               EmotionResult emotion, AgentEventSink sink) {
        String category = switch (intent) {
            case COMPLAINT -> "COMPLAINT";
            case ORDER_QUERY -> "ORDER";
            case LOGISTICS_QUERY -> "LOGISTICS";
            case POLICY_QUERY -> "POLICY";
            default -> "OTHER";
        };
        String priority = emotion.score() >= 0.9 ? "URGENT" : "HIGH";
        String title = intent == Intent.HUMAN_AGENT ? "客户请求人工客服" : "客户情绪负面，自动升级";
        Ticket ticket = ticketService.create(conversation.getId(), category, title,
                userText, priority, "AGENT", conversation.getUserName());
        conversationService.updateStatus(conversation.getId(), "HUMAN_PENDING");

        Map<String, Object> hd = new LinkedHashMap<>();
        hd.put("ticketNo", ticket.getTicketNo());
        hd.put("priority", ticket.getPriority());
        hd.put("reason", intent == Intent.HUMAN_AGENT ? "客户主动要求转人工" : "检测到负面情绪，自动升级");
        sink.event("handoff", hd);

        String answer = String.format(
                "非常抱歉给您带来了不好的体验。我已为您创建优先级为「%s」的工单 %s，并转接至人工客服，"
                        + "稍后将有专员尽快与您联系为您跟进处理。感谢您的耐心与理解。",
                ticket.getPriority(), ticket.getTicketNo());
        streamText(answer, sink);
        log.info("会话 {} 转人工，工单 {}", conversation.getId(), ticket.getTicketNo());
        return new AgentReply(answer, intent, emotion, ticket, true);
    }

    // ---------------- 自动应答（工具调用 + 流式） ----------------

    private AgentReply autoAnswer(Conversation conversation, String userText, Intent intent,
                                  EmotionResult emotion, AgentEventSink sink) {
        List<ChatMsg> messages = new ArrayList<>();
        messages.add(ChatMsg.system(props.getAgent().getSystemPrompt()));
        Faq faq = faqService.bestMatch(userText);
        if (faq != null) {
            messages.add(ChatMsg.system("FAQ_CONTEXT:" + faq.getAnswer()));
        }
        for (Message m : conversationService.history(conversation.getId(),
                props.getAgent().getHistoryTurns() * 2)) {
            if ("user".equals(m.getRole())) {
                messages.add(ChatMsg.user(m.getContent()));
            } else if ("assistant".equals(m.getRole()) && m.getContent() != null) {
                messages.add(ChatMsg.assistant(m.getContent()));
            }
        }
        messages.add(ChatMsg.user(userText));

        LlmChatRequest planReq = new LlmChatRequest(new ArrayList<>(messages), toolRegistry.specs());
        LlmChatResult plan = llm.chat(planReq);

        StringBuilder answer = new StringBuilder();
        if (plan.hasToolCalls()) {
            messages.add(ChatMsg.assistantToolCalls(plan.toolCalls()));
            for (ToolCall call : plan.toolCalls()) {
                Map<String, Object> args = parseArgs(call.arguments());
                args.put("conversation_id", String.valueOf(conversation.getId()));
                args.put("customer", conversation.getUserName());

                Map<String, Object> callEv = new LinkedHashMap<>();
                callEv.put("name", call.name());
                callEv.put("arguments", args);
                sink.event("tool_call", callEv);

                ToolResult tr = toolRegistry.execute(call.name(), args);

                Map<String, Object> resEv = new LinkedHashMap<>();
                resEv.put("name", call.name());
                resEv.put("ok", tr.ok());
                resEv.put("summary", tr.summary());
                resEv.put("data", tr.data());
                sink.event("tool_result", resEv);

                conversationService.addMessage(conversation.getId(), "tool", tr.summary(),
                        intent.name(), null, null, call.name(), toJson(args), toJson(tr.data()));

                messages.add(ChatMsg.tool(call.id(), call.name(), tr.summary()));
            }
            LlmChatRequest finalReq = new LlmChatRequest(messages, toolRegistry.specs());
            finalReq.setAllowTools(false);
            llm.chatStream(finalReq, token -> {
                answer.append(token);
                sink.event("token", Map.of("text", token));
            });
        } else if (plan.content() != null && !plan.content().isBlank()) {
            streamText(plan.content(), sink);
            answer.append(plan.content());
        } else {
            LlmChatRequest finalReq = new LlmChatRequest(messages, toolRegistry.specs());
            finalReq.setAllowTools(false);
            llm.chatStream(finalReq, token -> {
                answer.append(token);
                sink.event("token", Map.of("text", token));
            });
        }
        return new AgentReply(answer.toString(), intent, emotion, null, false);
    }

    // ---------------- 工具方法 ----------------

    private void streamText(String text, AgentEventSink sink) {
        int step = 6;
        for (int i = 0; i < text.length(); i += step) {
            sink.event("token", Map.of("text", text.substring(i, Math.min(text.length(), i + step))));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgs(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return om.readValue(json, LinkedHashMap.class);
        } catch (Exception e) {
            log.warn("解析工具参数失败：{}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private String toJson(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return om.writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }
}
