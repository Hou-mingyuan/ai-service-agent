package com.portfolio.csagent.controller;

import java.util.List;

import com.portfolio.csagent.common.ApiResponse;
import com.portfolio.csagent.entity.Conversation;
import com.portfolio.csagent.entity.Message;
import com.portfolio.csagent.service.ConversationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping
    public ApiResponse<List<Conversation>> list(@RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(conversationService.list(limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<Conversation> get(@PathVariable Long id) {
        return ApiResponse.ok(conversationService.getById(id));
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<List<Message>> messages(@PathVariable Long id) {
        return ApiResponse.ok(conversationService.messages(id));
    }
}
