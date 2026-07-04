package com.portfolio.csagent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TicketCreateRequest {

    private Long conversationId;
    private String category;

    @NotBlank(message = "工单标题不能为空")
    private String title;

    private String description;
    private String priority;
    private String customer;
}
