package com.portfolio.csagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MessageSendRequest(
        @NotBlank(message = "消息幂等ID不能为空")
        @Pattern(regexp = "[A-Za-z0-9._:-]{8,64}", message = "消息幂等ID格式不正确") String clientMessageId,
        @NotBlank(message = "消息内容不能为空")
        @Size(max = 4000, message = "单条消息不能超过4000字符") String content) {
}
