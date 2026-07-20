package com.portfolio.csagent.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.portfolio.csagent.common.BizException;
import com.portfolio.csagent.config.AppProperties;
import com.portfolio.csagent.entity.Conversation;
import com.portfolio.csagent.entity.Message;
import com.portfolio.csagent.mapper.ConversationMapper;
import com.portfolio.csagent.mapper.MessageMapper;
import com.portfolio.csagent.security.AuthenticatedUser;
import com.portfolio.csagent.security.CurrentActor;
import com.portfolio.csagent.security.Permission;
import com.portfolio.csagent.security.Role;
import com.portfolio.csagent.agent.IntentResult;
import com.portfolio.csagent.agent.EmotionResult;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {
    public static final String BOT = "BOT";
    public static final String HUMAN_PENDING = "HUMAN_PENDING";
    public static final String HUMAN = "HUMAN";
    public static final String CLOSED = "CLOSED";

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final CurrentActor currentActor;
    private final RealtimeEventService realtimeEventService;
    private final AuditService auditService;
    private final AppProperties properties;

    public ConversationService(ConversationMapper conversationMapper, MessageMapper messageMapper,
                               CurrentActor currentActor, RealtimeEventService realtimeEventService,
                               AuditService auditService, AppProperties properties) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.currentActor = currentActor;
        this.realtimeEventService = realtimeEventService;
        this.auditService = auditService;
        this.properties = properties;
    }

    @Transactional
    public Conversation createFor(AuthenticatedUser customer, String channel) {
        if (customer.role() != Role.CUSTOMER && customer.role() != Role.ADMIN) {
            throw new BizException(403, "只有客户账号可以创建客户会话");
        }
        Conversation conversation = new Conversation();
        conversation.setTenantId(customer.tenantId());
        conversation.setSessionKey("CS-" + UUID.randomUUID().toString().replace("-", ""));
        conversation.setCustomerUsername(customer.username());
        conversation.setUserName(customer.displayName());
        conversation.setChannel(channel == null ? "web" : channel);
        conversation.setStatus(BOT);
        conversation.setBotEnabled(1);
        conversation.setResolved(0);
        conversation.setVersion(0);
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        conversation.setLastMessageAt(LocalDateTime.now());
        conversationMapper.insert(conversation);
        return conversation;
    }

    /** Test/demo compatibility helper. Runtime requests use createFor with authenticated ownership. */
    public Conversation create(String userName, String channel) {
        return createFor(new AuthenticatedUser(0L, properties.getDemo().getTenantId(), "customer",
                userName == null ? "演示客户" : userName, Role.CUSTOMER), channel);
    }

    public Conversation getOrCreate(Long conversationId, AuthenticatedUser customer) {
        if (conversationId == null) {
            return createFor(customer, "web");
        }
        Conversation conversation = requireAccessible(conversationId, customer);
        if (CLOSED.equals(conversation.getStatus())) {
            throw new BizException(409, "该会话已关闭，请新建会话");
        }
        return conversation;
    }

    public Optional<ExistingMessage> findOwnedMessage(AuthenticatedUser actor, String clientMessageId) {
        Message message = findByClientId(actor.tenantId(), clientMessageId);
        if (message == null || !actor.username().equals(message.getSenderUsername())) {
            return Optional.empty();
        }
        Conversation conversation = conversationMapper.selectOne(Wrappers.<Conversation>lambdaQuery()
                .eq(Conversation::getTenantId, actor.tenantId())
                .eq(Conversation::getId, message.getConversationId())
                .eq(Conversation::getCustomerUsername, actor.username())
                .last("limit 1"));
        return conversation == null ? Optional.empty() : Optional.of(new ExistingMessage(conversation, message));
    }

    public Conversation getById(Long id) {
        return requireAccessible(id, currentActor.require());
    }

    public Conversation requireAccessible(Long id, AuthenticatedUser actor) {
        Conversation conversation = conversationMapper.selectOne(Wrappers.<Conversation>lambdaQuery()
                .eq(Conversation::getTenantId, actor.tenantId())
                .eq(Conversation::getId, id)
                .last("limit 1"));
        if (conversation == null || !visibleTo(conversation, actor)) {
            throw new BizException(404, "会话不存在");
        }
        return conversation;
    }

    public boolean visibleTo(Conversation conversation, AuthenticatedUser actor) {
        if (actor.role() == Role.CUSTOMER) {
            return actor.username().equals(conversation.getCustomerUsername());
        }
        if (actor.role().isSupervisorOrAbove()) {
            return true;
        }
        return HUMAN_PENDING.equals(conversation.getStatus()) && conversation.getAssignedAgent() == null
                || actor.username().equals(conversation.getAssignedAgent());
    }

    @Transactional
    public MessageResult addCustomerMessage(Conversation conversation, AuthenticatedUser customer,
                                            String clientMessageId, String content) {
        if (!customer.username().equals(conversation.getCustomerUsername())) {
            throw new BizException(404, "会话不存在");
        }
        MessageResult result = insertIdempotent(conversation, clientMessageId, "user", customer.username(),
                customer.displayName(), content);
        if (!result.replayed() && (HUMAN_PENDING.equals(conversation.getStatus())
                || HUMAN.equals(conversation.getStatus()))) {
            realtimeEventService.publishAgents(customer.tenantId(), "message.customer", result.message());
        }
        return result;
    }

    @Transactional
    public MessageResult sendHumanMessage(Long conversationId, String clientMessageId, String content) {
        AuthenticatedUser actor = currentActor.require();
        Conversation conversation = requireAccessible(conversationId, actor);
        if (actor.role() == Role.CUSTOMER) {
            if (!(HUMAN_PENDING.equals(conversation.getStatus()) || HUMAN.equals(conversation.getStatus()))) {
                throw new BizException(409, "当前会话尚未进入人工接管");
            }
            return addCustomerMessage(conversation, actor, clientMessageId, content);
        }
        if (!HUMAN.equals(conversation.getStatus())) {
            throw new BizException(409, "请先认领会话再回复客户");
        }
        if (actor.role() == Role.AGENT && !actor.username().equals(conversation.getAssignedAgent())) {
            throw new BizException(404, "会话不存在");
        }
        MessageResult result = insertIdempotent(conversation, clientMessageId, "agent", actor.username(),
                actor.displayName(), content);
        if (!result.replayed()) {
            setFirstResponse(conversation);
            realtimeEventService.publishUser(actor.tenantId(), conversation.getCustomerUsername(),
                    "message.agent", result.message());
            realtimeEventService.publishAgents(actor.tenantId(), "message.agent", result.message());
            auditService.record(actor, "CONVERSATION_REPLY", "CONVERSATION", conversationId,
                    "SUCCESS", clientMessageId, Map.of("messageId", result.message().getId()));
        }
        return result;
    }

    @Transactional
    public Conversation claim(Long conversationId) {
        AuthenticatedUser actor = currentActor.require();
        if (!actor.role().isStaff() || !actor.role().getPermissions().contains(Permission.CONVERSATION_QUEUE)) {
            throw new BizException(403, "没有认领会话的权限");
        }
        Conversation existing = conversationMapper.selectOne(Wrappers.<Conversation>lambdaQuery()
                .eq(Conversation::getTenantId, actor.tenantId())
                .eq(Conversation::getId, conversationId)
                .last("limit 1"));
        if (existing == null) {
            throw new BizException(404, "会话不存在");
        }
        if (actor.username().equals(existing.getAssignedAgent()) && HUMAN.equals(existing.getStatus())) {
            return existing;
        }
        int updated = conversationMapper.claim(conversationId, actor.tenantId(), actor.username(), LocalDateTime.now());
        if (updated != 1) {
            throw new BizException(409, "该会话已被其他坐席认领");
        }
        Conversation claimed = conversationMapper.selectById(conversationId);
        auditService.record(actor, "CONVERSATION_CLAIM", "CONVERSATION", conversationId,
                "SUCCESS", null, Map.of("customer", claimed.getCustomerUsername()));
        realtimeEventService.publishUser(actor.tenantId(), claimed.getCustomerUsername(),
                "conversation.claimed", summary(claimed));
        realtimeEventService.publishAgents(actor.tenantId(), "conversation.claimed", summary(claimed));
        return claimed;
    }

    @Transactional
    public Conversation requestHandoff(Conversation conversation, String reason) {
        if (HUMAN_PENDING.equals(conversation.getStatus()) || HUMAN.equals(conversation.getStatus())) {
            return conversation;
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = conversationMapper.requestHandoff(conversation.getId(), conversation.getTenantId(),
                reason, now, conversation.getVersion());
        if (updated != 1) {
            throw new BizException(409, "会话状态已变化，请重试");
        }
        Conversation pending = conversationMapper.selectById(conversation.getId());
        realtimeEventService.publishAgents(pending.getTenantId(), "conversation.handoff", summary(pending));
        realtimeEventService.publishUser(pending.getTenantId(), pending.getCustomerUsername(),
                "conversation.handoff", summary(pending));
        return pending;
    }

    @Transactional
    public Conversation resumeBot(Long conversationId, String note) {
        AuthenticatedUser actor = currentActor.require();
        Conversation conversation = requireAccessible(conversationId, actor);
        if (actor.role() == Role.CUSTOMER) {
            throw new BizException(403, "客户不能恢复机器人");
        }
        if (!HUMAN.equals(conversation.getStatus())) {
            throw new BizException(409, "只有人工接管中的会话可以恢复机器人");
        }
        if (conversationMapper.resumeBot(conversationId, actor.tenantId(), LocalDateTime.now(),
                conversation.getVersion()) != 1) {
            throw new BizException(409, "会话状态已变化，请刷新后重试");
        }
        Conversation resumed = conversationMapper.selectById(conversationId);
        auditService.record(actor, "BOT_RESUME", "CONVERSATION", conversationId, "SUCCESS", null,
                Map.of("note", note == null ? "" : note));
        realtimeEventService.publishUser(actor.tenantId(), resumed.getCustomerUsername(),
                "conversation.bot_resumed", summary(resumed));
        return resumed;
    }

    @Transactional
    public int markRead(Long conversationId, Long upToId) {
        AuthenticatedUser actor = currentActor.require();
        Conversation conversation = requireAccessible(conversationId, actor);
        int count = messageMapper.markRead(actor.tenantId(), conversationId, upToId,
                actor.username(), LocalDateTime.now());
        Object payload = Map.of("conversationId", conversationId, "upToId", upToId,
                "reader", actor.username());
        if (actor.role() == Role.CUSTOMER) {
            realtimeEventService.publishAgents(actor.tenantId(), "message.read", payload);
        } else {
            realtimeEventService.publishUser(actor.tenantId(), conversation.getCustomerUsername(),
                    "message.read", payload);
        }
        return count;
    }

    public IPage<Conversation> list(String mode, int page, int size) {
        AuthenticatedUser actor = currentActor.require();
        var query = Wrappers.<Conversation>lambdaQuery()
                .eq(Conversation::getTenantId, actor.tenantId());
        if (actor.role() == Role.CUSTOMER) {
            query.eq(Conversation::getCustomerUsername, actor.username());
        } else if ("queue".equalsIgnoreCase(mode)) {
            query.eq(Conversation::getStatus, HUMAN_PENDING).isNull(Conversation::getAssignedAgent);
        } else if (actor.role() == Role.AGENT || "assigned".equalsIgnoreCase(mode)) {
            query.eq(Conversation::getAssignedAgent, actor.username());
        }
        query.orderByDesc(Conversation::getUpdatedAt);
        return conversationMapper.selectPage(Page.of(Math.max(1, page), Math.min(100, Math.max(1, size))), query);
    }

    public List<Message> messages(Long conversationId, long afterId, int limit) {
        AuthenticatedUser actor = currentActor.require();
        requireAccessible(conversationId, actor);
        return messageMapper.selectList(Wrappers.<Message>lambdaQuery()
                .eq(Message::getTenantId, actor.tenantId())
                .eq(Message::getConversationId, conversationId)
                .gt(Message::getId, Math.max(0, afterId))
                .orderByAsc(Message::getId)
                .last("limit " + Math.min(500, Math.max(1, limit))));
    }

    public List<Message> history(Long conversationId, int limit) {
        List<Message> descending = messageMapper.selectList(Wrappers.<Message>lambdaQuery()
                .eq(Message::getConversationId, conversationId)
                .orderByDesc(Message::getId)
                .last("limit " + Math.min(100, Math.max(1, limit))));
        java.util.Collections.reverse(descending);
        return descending;
    }

    public Message addMessage(Long conversationId, String role, String content) {
        return addMessage(conversationId, role, content, null, null, null, null, null, null);
    }

    public Message addMessage(Long conversationId, String role, String content, String intent,
                              String sentiment, Double sentimentScore, String toolName,
                              String toolArgs, String toolResult) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BizException(404, "会话不存在");
        }
        Message message = baseMessage(conversation, null, role, "system", "系统", content);
        message.setIntent(intent);
        message.setSentiment(sentiment);
        if (sentimentScore != null) {
            message.setSentimentScore(BigDecimal.valueOf(sentimentScore));
        }
        message.setToolName(toolName);
        message.setToolArgs(toolArgs);
        message.setToolResult(toolResult);
        messageMapper.insert(message);
        touch(conversation, !"user".equals(role));
        return message;
    }

    public void updateMeta(Conversation conversation, String intent, String sentiment) {
        conversation.setLastIntent(intent);
        conversation.setLastSentiment(sentiment);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conversation);
    }

    public void updateClassification(Long messageId, IntentResult intent, EmotionResult emotion) {
        Message message = messageMapper.selectById(messageId);
        if (message == null) {
            return;
        }
        message.setIntent(intent.intent().name());
        message.setIntentConfidence(BigDecimal.valueOf(intent.confidence()));
        message.setSentiment(emotion.sentiment().name());
        message.setSentimentScore(BigDecimal.valueOf(emotion.score()));
        message.setClassificationSource(intent.source());
        messageMapper.updateById(message);
    }

    @Transactional
    public void close(Long conversationId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation != null) {
            conversation.setStatus(CLOSED);
            conversation.setBotEnabled(0);
            conversation.setResolved(1);
            conversation.setUpdatedAt(LocalDateTime.now());
            conversationMapper.updateById(conversation);
        }
    }

    private MessageResult insertIdempotent(Conversation conversation, String clientMessageId, String role,
                                           String senderUsername, String senderName, String content) {
        Message existing = findByClientId(conversation.getTenantId(), clientMessageId);
        if (existing != null) {
            if (!existing.getConversationId().equals(conversation.getId())
                    || !senderUsername.equals(existing.getSenderUsername())) {
                throw new BizException(409, "消息幂等ID已被其他请求使用");
            }
            return new MessageResult(existing, true);
        }
        Message message = baseMessage(conversation, clientMessageId, role, senderUsername, senderName, content);
        try {
            messageMapper.insert(message);
        } catch (DataIntegrityViolationException exception) {
            Message replay = findByClientId(conversation.getTenantId(), clientMessageId);
            if (replay == null) {
                throw exception;
            }
            return new MessageResult(replay, true);
        }
        touch(conversation, false);
        return new MessageResult(message, false);
    }

    private Message baseMessage(Conversation conversation, String clientMessageId, String role,
                                String senderUsername, String senderName, String content) {
        Message message = new Message();
        message.setTenantId(conversation.getTenantId());
        message.setConversationId(conversation.getId());
        message.setClientMessageId(clientMessageId);
        message.setRole(role);
        message.setSenderUsername(senderUsername);
        message.setSenderName(senderName);
        message.setContent(content.trim());
        message.setDeliveryStatus("SENT");
        message.setCreatedAt(LocalDateTime.now());
        return message;
    }

    private Message findByClientId(String tenantId, String clientMessageId) {
        if (clientMessageId == null) {
            return null;
        }
        return messageMapper.selectOne(Wrappers.<Message>lambdaQuery()
                .eq(Message::getTenantId, tenantId)
                .eq(Message::getClientMessageId, clientMessageId)
                .last("limit 1"));
    }

    private void touch(Conversation conversation, boolean response) {
        LocalDateTime now = LocalDateTime.now();
        conversation.setLastMessageAt(now);
        conversation.setUpdatedAt(now);
        if (response && conversation.getFirstResponseAt() == null) {
            conversation.setFirstResponseAt(now);
        }
        conversationMapper.updateById(conversation);
    }

    private void setFirstResponse(Conversation conversation) {
        if (conversation.getFirstResponseAt() == null) {
            conversation.setFirstResponseAt(LocalDateTime.now());
            conversation.setUpdatedAt(LocalDateTime.now());
            conversationMapper.updateById(conversation);
        }
    }

    private Map<String, Object> summary(Conversation conversation) {
        return Map.of(
                "id", conversation.getId(),
                "sessionKey", conversation.getSessionKey(),
                "customer", conversation.getUserName(),
                "status", conversation.getStatus(),
                "assignedAgent", conversation.getAssignedAgent() == null ? "" : conversation.getAssignedAgent(),
                "handoffReason", conversation.getHandoffReason() == null ? "" : conversation.getHandoffReason());
    }

    public record MessageResult(Message message, boolean replayed) {
    }

    public record ExistingMessage(Conversation conversation, Message message) {
    }
}
