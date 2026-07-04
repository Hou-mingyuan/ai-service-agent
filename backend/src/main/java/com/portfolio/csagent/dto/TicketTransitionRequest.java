package com.portfolio.csagent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TicketTransitionRequest {

    @NotBlank(message = "目标状态不能为空")
    private String toStatus;

    private String operator;
    private String note;
}
