package com.portfolio.csagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TicketReopenRequest(
        @NotBlank(message = "重开原因不能为空") @Size(max = 1000, message = "重开原因不能超过1000字符") String reason) {
}
