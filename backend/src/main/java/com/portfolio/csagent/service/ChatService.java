package com.portfolio.csagent.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.portfolio.csagent.agent.AgentEventSink;
import com.portfolio.csagent.agent.AgentOrchestrator;
import com.portfolio.csagent.agent.AgentReply;
import com.portfolio.csagent.agent.AgentRequestContext;
import com.portfolio.csagent.common.BizException;
import com.portfolio.csagent.common.RequestIdContext;
import com.portfolio.csagent.config.AppProperties;
import com.portfolio.csagent.dto.ChatRequest;
import com.portfolio.csagent.entity.Conversation;
import com.portfolio.csagent.entity.Message;
import com.portfolio.csagent.security.AuthenticatedUser;
import com.portfolio.csagent.security.CurrentActor;
import com.portfolio.csagent.security.Role;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ConversationService conversationService;
    private final AgentOrchestrator orchestrator;
    private final CurrentActor currentActor;
    private final AuditService auditService;
    private final Executor chatExecutor;
    private final AppProperties properties;
    private final Timer chatTimer;
    private final Counter cancelledCounter;
    private final Counter failureCounter;

    public ChatService(ConversationService conversationService, AgentOrchestrator orchestrator,
                       CurrentActor currentActor, AuditService auditService,
                       @Qualifier("chatExecutor") Executor chatExecutor,
                       AppProperties properties, MeterRegistry meterRegistry) {
        this.conversationService = conversationService;
        this.orchestrator = orchestrator;
        this.currentActor = currentActor;
        this.auditService = auditService;
        this.chatExecutor = chatExecutor;
        this.properties = properties;
        this.chatTimer = meterRegistry.timer("csagent.chat.duration");
        this.cancelledCounter = meterRegistry.counter("csagent.chat.cancelled");
        this.failureCounter = meterRegistry.counter("csagent.chat.failures");
    }

    public SseEmitter stream(ChatRequest request) {
        AuthenticatedUser actor = currentActor.require();
        if (actor.role() != Role.CUSTOMER && actor.role() != Role.ADMIN) {
            throw new BizException(403, "当前角色不能发起客户对话");
        }
        Conversation conversation;
        ConversationService.MessageResult userResult;
        var existing = conversationService.findOwnedMessage(actor, request.getClientMessageId());
        if (existing.isPresent()) {
            conversation = existing.get().conversation();
            userResult = new ConversationService.MessageResult(existing.get().message(), true);
        } else {
            conversation = conversationService.getOrCreate(request.getConversationId(), actor);
            userResult = conversationService.addCustomerMessage(
                    conversation, actor, request.getClientMessageId(), request.getMessage().trim());
        }

        AtomicBoolean cancelled = new AtomicBoolean(false);
        SseEmitter emitter = new SseEmitter(properties.getAgent().getSseTimeoutSeconds() * 1000L);
        emitter.onCompletion(() -> cancelled.set(true));
        emitter.onTimeout(() -> {
            cancelled.set(true);
            emitter.complete();
        });
        emitter.onError(error -> cancelled.set(true));
        AgentEventSink sink = sink(emitter, cancelled);

        sendStart(sink, conversation, userResult.message(), userResult.replayed());
        if (userResult.replayed()) {
            sink.event("replay", Map.of("messageId", userResult.message().getId(),
                    "conversationId", conversation.getId()));
            sink.event("done", Map.of("conversationId", conversation.getId(), "replayed", true,
                    "mode", conversation.getStatus()));
            emitter.complete();
            return emitter;
        }

        if (ConversationService.HUMAN_PENDING.equals(conversation.getStatus())
                || ConversationService.HUMAN.equals(conversation.getStatus())) {
            sink.event("meta", Map.of("mode", "HUMAN", "conversationStatus", conversation.getStatus()));
            sink.event("done", Map.of("conversationId", conversation.getId(), "replayed", false,
                    "mode", "HUMAN"));
            emitter.complete();
            return emitter;
        }

        String requestId = RequestIdContext.current();
        try {
            chatExecutor.execute(() -> process(request, actor, conversation, userResult.message(), requestId,
                    cancelled, sink, emitter));
        } catch (RejectedExecutionException exception) {
            failureCounter.increment();
            throw new BizException(503, "当前对话请求较多，请稍后重试");
        }
        return emitter;
    }

    private void process(ChatRequest request, AuthenticatedUser actor, Conversation conversation,
                         Message userMessage, String requestId, AtomicBoolean cancelled,
                         AgentEventSink sink, SseEmitter emitter) {
        RequestIdContext.set(requestId);
        Timer.Sample sample = Timer.start();
        try {
            AgentRequestContext context = new AgentRequestContext(actor, request.getClientMessageId(), requestId,
                    userMessage.getId(), cancelled);
            AgentReply reply = orchestrator.handle(conversation, request.getMessage(), context, sink);
            if (cancelled.get()) {
                throw new CancellationException("client disconnected");
            }
            Message assistant = conversationService.addMessage(conversation.getId(), "assistant", reply.answer(),
                    reply.intent().name(), reply.emotion().sentiment().name(), reply.emotion().score(),
                    null, null, null);
            Map<String, Object> done = new LinkedHashMap<>();
            done.put("conversationId", conversation.getId());
            done.put("messageId", assistant.getId());
            done.put("handoff", reply.handoff());
            done.put("ticketNo", reply.ticket() == null ? null : reply.ticket().getTicketNo());
            done.put("mode", reply.handoff() ? "HUMAN_PENDING" : "BOT");
            done.put("replayed", false);
            sink.event("done", done);
            auditService.record(actor, "CHAT_COMPLETE", "CONVERSATION", conversation.getId(), "SUCCESS",
                    request.getClientMessageId(), Map.of("intent", reply.intent().name(),
                            "intentConfidence", reply.intentConfidence(), "handoff", reply.handoff(),
                            "citations", reply.citations().size()));
            emitter.complete();
        } catch (CancellationException | UncheckedIOException exception) {
            cancelledCounter.increment();
            log.info("Chat cancelled conversationId={} requestId={}", conversation.getId(), requestId);
            emitter.complete();
        } catch (Exception exception) {
            failureCounter.increment();
            String message = exception instanceof BizException biz
                    ? biz.getMessage() : "对话处理暂时失败，请重试或转人工";
            try {
                sink.event("error", Map.of("message", message, "requestId", requestId));
            } catch (Exception ignored) {
                // Connection is already gone.
            }
            try {
                auditService.record(actor, "CHAT_FAILED", "CONVERSATION", conversation.getId(), "FAILED",
                        request.getClientMessageId(), Map.of("errorType", exception.getClass().getSimpleName()));
            } catch (Exception auditFailure) {
                log.error("Chat audit failed requestId={}", requestId, auditFailure);
            }
            log.warn("Chat failed conversationId={} requestId={} type={}", conversation.getId(), requestId,
                    exception.getClass().getSimpleName());
            emitter.complete();
        } finally {
            sample.stop(chatTimer);
            RequestIdContext.clear();
        }
    }

    private AgentEventSink sink(SseEmitter emitter, AtomicBoolean cancelled) {
        return (type, data) -> {
            if (cancelled.get()) {
                throw new CancellationException("client disconnected");
            }
            try {
                emitter.send(SseEmitter.event().name(type).data(data, MediaType.APPLICATION_JSON));
            } catch (IOException exception) {
                cancelled.set(true);
                throw new UncheckedIOException(exception);
            }
        };
    }

    private void sendStart(AgentEventSink sink, Conversation conversation, Message userMessage, boolean replayed) {
        sink.event("start", Map.of("conversationId", conversation.getId(),
                "sessionKey", conversation.getSessionKey(), "userMessageId", userMessage.getId(),
                "replayed", replayed, "conversationStatus", conversation.getStatus()));
    }
}
