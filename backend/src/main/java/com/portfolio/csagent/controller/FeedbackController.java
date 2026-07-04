package com.portfolio.csagent.controller;

import com.portfolio.csagent.common.ApiResponse;
import com.portfolio.csagent.dto.FeedbackRequest;
import com.portfolio.csagent.entity.Feedback;
import com.portfolio.csagent.service.FeedbackService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public ApiResponse<Feedback> submit(@Valid @RequestBody FeedbackRequest req) {
        return ApiResponse.ok(feedbackService.submit(
                req.getConversationId(), req.getRating(), req.getComment()));
    }
}
