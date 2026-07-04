package com.portfolio.csagent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TicketAssignRequest {

    @NotBlank(message = "处理人不能为空")
    private String assignee;
}
