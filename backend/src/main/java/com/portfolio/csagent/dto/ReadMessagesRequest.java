package com.portfolio.csagent.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReadMessagesRequest(
        @NotNull(message = "已读消息ID不能为空") @Positive(message = "已读消息ID必须为正数") Long upToId) {
}
