package com.portfolio.csagent.controller;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.portfolio.csagent.common.ApiResponse;
import com.portfolio.csagent.dto.MessageSendRequest;
import com.portfolio.csagent.dto.ReadMessagesRequest;
import com.portfolio.csagent.entity.Conversation;
import com.portfolio.csagent.entity.Message;
import com.portfolio.csagent.service.ConversationService;
import com.portfolio.csagent.service.HandoffService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {
    private final ConversationService conversationService;
    private final HandoffService handoffService;

    public ConversationController(ConversationService conversationService, HandoffService handoffService) {
        this.conversationService = conversationService;
        this.handoffService = handoffService;
    }

    @GetMapping
    public ApiResponse<IPage<Conversation>> list(@RequestParam(defaultValue = "mine") String mode,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.ok(conversationService.list(mode, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<Conversation> get(@PathVariable Long id) {
        return ApiResponse.ok(conversationService.getById(id));
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<List<Message>> messages(@PathVariable Long id,
                                                @RequestParam(defaultValue = "0") long afterId,
                                                @RequestParam(defaultValue = "200") int limit) {
        return ApiResponse.ok(conversationService.messages(id, afterId, limit));
    }

    @PostMapping("/{id}/claim")
    public ApiResponse<HandoffService.ClaimResult> claim(@PathVariable Long id) {
        return ApiResponse.ok(handoffService.claim(id));
    }

    @PostMapping("/{id}/messages")
    public ApiResponse<ConversationService.MessageResult> send(@PathVariable Long id,
                                                                @Valid @RequestBody MessageSendRequest request) {
        return ApiResponse.ok(conversationService.sendHumanMessage(id, request.clientMessageId(), request.content()));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Map<String, Integer>> read(@PathVariable Long id,
                                                  @Valid @RequestBody ReadMessagesRequest request) {
        return ApiResponse.ok(Map.of("updated", conversationService.markRead(id, request.upToId())));
    }

    @PostMapping("/{id}/bot/resume")
    public ApiResponse<Conversation> resumeBot(@PathVariable Long id,
                                                @RequestBody(required = false) Map<String, String> body) {
        String note = body == null ? null : body.get("note");
        return ApiResponse.ok(conversationService.resumeBot(id, note));
    }
}
