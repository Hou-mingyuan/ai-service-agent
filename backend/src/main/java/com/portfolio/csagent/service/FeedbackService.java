package com.portfolio.csagent.service;

import java.time.LocalDateTime;

import com.portfolio.csagent.common.BizException;
import com.portfolio.csagent.entity.Feedback;
import com.portfolio.csagent.mapper.FeedbackMapper;
import org.springframework.stereotype.Service;

@Service
public class FeedbackService {

    private final FeedbackMapper feedbackMapper;
    private final ConversationService conversationService;

    public FeedbackService(FeedbackMapper feedbackMapper, ConversationService conversationService) {
        this.feedbackMapper = feedbackMapper;
        this.conversationService = conversationService;
    }

    public Feedback submit(Long conversationId, Integer rating, String comment) {
        if (rating == null || rating < 1 || rating > 5) {
            throw new BizException("评分需为 1~5 分");
        }
        Feedback f = new Feedback();
        f.setConversationId(conversationId);
        f.setRating(rating);
        f.setComment(comment);
        f.setCreatedAt(LocalDateTime.now());
        feedbackMapper.insert(f);
        conversationService.updateStatus(conversationId, "CLOSED");
        return f;
    }
}
