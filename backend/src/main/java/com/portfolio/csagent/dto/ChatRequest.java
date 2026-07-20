package com.portfolio.csagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ChatRequest {
    private Long conversationId;

    @NotBlank(message = "消息幂等ID不能为空")
    @Pattern(regexp = "[A-Za-z0-9._:-]{8,64}", message = "消息幂等ID格式不正确")
    private String clientMessageId;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 4000, message = "单条消息不能超过4000字符")
    private String message;

    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public String getClientMessageId() { return clientMessageId; }
    public void setClientMessageId(String clientMessageId) { this.clientMessageId = clientMessageId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
