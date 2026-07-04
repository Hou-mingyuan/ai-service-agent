package com.portfolio.csagent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequest {

    /** 为空则新建会话 */
    private Long conversationId;

    @NotBlank(message = "消息内容不能为空")
    private String message;

    private String userName;
}
