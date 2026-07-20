package com.portfolio.csagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TicketCreateRequest {

    private Long conversationId;
    private String category;

    @NotBlank(message = "工单标题不能为空")
    @Size(max = 255, message = "工单标题不能超过255字符")
    private String title;

    @Size(max = 4000, message = "工单描述不能超过4000字符")
    private String description;
    @Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT", message = "工单优先级不正确")
    private String priority;
}
