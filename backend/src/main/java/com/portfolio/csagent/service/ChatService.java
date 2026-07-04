package com.portfolio.csagent.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import com.portfolio.csagent.agent.AgentEventSink;
import com.portfolio.csagent.agent.AgentOrchestrator;
import com.portfolio.csagent.agent.AgentReply;
import com.portfolio.csagent.dto.ChatRequest;
import com.portfolio.csagent.entity.Conversation;
import com.portfolio.csagent.entity.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ConversationService conversationService;
    private final AgentOrchestrator orchestrator;
    private final Executor chatExecutor;

    public ChatService(ConversationService conversationService, AgentOrchestrator orchestrator,
                       Executor chatExecutor) {
        this.conversationService = conversationService;
        this.orchestrator = orchestrator;
        this.chatExecutor = chatExecutor;
    }

    public SseEmitter stream(ChatRequest req) {
        Conversation conversation = conversationService.getOrCreate(
                req.getConversationId(), req.getUserName());
        Message userMsg = conversationService.addMessage(
                conversation.getId(), "user", req.getMessage());

        SseEmitter emitter = new SseEmitter(180_000L);
        AgentEventSink sink = (type, data) -> {
            try {
                emitter.send(SseEmitter.event().name(type).data(data, MediaType.APPLICATION_JSON));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };

        chatExecutor.execute(() -> {
            try {
                sink.event("start", Map.of(
                        "conversationId", conversation.getId(),
                        "userMessageId", userMsg.getId()));

                AgentReply reply = orchestrator.handle(conversation, req.getMessage(), sink);

                Message assistantMsg = conversationService.addMessage(
                        conversation.getId(), "assistant", reply.answer(),
                        reply.intent().name(), reply.emotion().sentiment().name(),
                        reply.emotion().score(), null, null, null);

                Map<String, Object> done = new LinkedHashMap<>();
                done.put("conversationId", conversation.getId());
                done.put("messageId", assistantMsg.getId());
                done.put("handoff", reply.handoff());
                done.put("ticketNo", reply.ticket() == null ? null : reply.ticket().getTicketNo());
                sink.event("done", done);
                emitter.complete();
            } catch (UncheckedIOException e) {
                log.debug("客户端断开连接：{}", e.getMessage());
                emitter.complete();
            } catch (Exception e) {
                log.warn("对话处理异常：{}", e.getMessage(), e);
                try {
                    sink.event("error", Map.of("message", String.valueOf(e.getMessage())));
                } catch (Exception ignore) {
                    // 已断开
                }
                emitter.complete();
            }
        });
        return emitter;
    }
}
