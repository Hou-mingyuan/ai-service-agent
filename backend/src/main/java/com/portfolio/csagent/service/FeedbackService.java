package com.portfolio.csagent.service;

import java.time.LocalDateTime;
import java.util.Map;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.portfolio.csagent.common.BizException;
import com.portfolio.csagent.entity.Conversation;
import com.portfolio.csagent.entity.Feedback;
import com.portfolio.csagent.mapper.FeedbackMapper;
import com.portfolio.csagent.security.AuthenticatedUser;
import com.portfolio.csagent.security.CurrentActor;
import com.portfolio.csagent.security.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedbackService {
    private final FeedbackMapper feedbackMapper;
    private final ConversationService conversationService;
    private final TicketService ticketService;
    private final CurrentActor currentActor;
    private final AuditService auditService;
    private final RealtimeEventService realtimeEventService;

    public FeedbackService(FeedbackMapper feedbackMapper, ConversationService conversationService,
                           TicketService ticketService, CurrentActor currentActor,
                           AuditService auditService, RealtimeEventService realtimeEventService) {
        this.feedbackMapper = feedbackMapper;
        this.conversationService = conversationService;
        this.ticketService = ticketService;
        this.currentActor = currentActor;
        this.auditService = auditService;
        this.realtimeEventService = realtimeEventService;
    }

    @Transactional
    public Feedback submit(Long conversationId, Integer rating, String comment) {
        AuthenticatedUser actor = currentActor.require();
        if (actor.role() != Role.CUSTOMER) {
            throw new BizException(403, "只有客户可以提交服务评价");
        }
        Conversation conversation = conversationService.requireAccessible(conversationId, actor);
        Feedback existing = feedbackMapper.selectOne(Wrappers.<Feedback>lambdaQuery()
                .eq(Feedback::getTenantId, actor.tenantId())
                .eq(Feedback::getConversationId, conversationId)
                .last("limit 1"));
        if (existing != null) {
            return existing;
        }
        if (rating == null || rating < 1 || rating > 5) {
            throw new BizException(400, "评分需为 1~5 分");
        }
        if (conversation.getHandoffAt() != null) {
            ticketService.closeAfterFeedback(actor, conversationId, "客户评价后确认关闭");
        }

        Feedback feedback = new Feedback();
        feedback.setTenantId(actor.tenantId());
        feedback.setConversationId(conversationId);
        feedback.setCustomerUsername(actor.username());
        feedback.setRating(rating);
        feedback.setComment(comment == null || comment.isBlank() ? null : comment.trim());
        feedback.setCreatedAt(LocalDateTime.now());
        feedbackMapper.insert(feedback);
        conversationService.close(conversationId);
        auditService.record(actor, "FEEDBACK_SUBMIT", "CONVERSATION", conversationId, "SUCCESS",
                "feedback:" + conversationId, Map.of("rating", rating));
        realtimeEventService.publishAgents(actor.tenantId(), "conversation.closed",
                Map.of("conversationId", conversationId, "rating", rating));
        return feedback;
    }
}
