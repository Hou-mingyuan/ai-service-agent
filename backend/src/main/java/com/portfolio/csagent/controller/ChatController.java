package com.portfolio.csagent.controller;

import com.portfolio.csagent.dto.ChatRequest;
import com.portfolio.csagent.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.security.access.prepost.PreAuthorize;
import com.portfolio.csagent.security.Permission;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /** 智能客服对话（SSE 流式）。事件类型：start/meta/tool_call/tool_result/token/handoff/done/error。 */
    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('" + Permission.CHAT_SEND + "')")
    public SseEmitter chat(@Valid @RequestBody ChatRequest request) {
        return chatService.stream(request);
    }
}
