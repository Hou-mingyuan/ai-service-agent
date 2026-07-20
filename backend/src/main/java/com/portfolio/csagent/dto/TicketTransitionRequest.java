package com.portfolio.csagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TicketTransitionRequest {

    @NotBlank(message = "目标状态不能为空")
    @Pattern(regexp = "OPEN|IN_PROGRESS|PENDING|RESOLVED|CLOSED", message = "目标状态不正确")
    private String toStatus;

    @Size(max = 1000, message = "流转说明不能超过1000字符")
    private String note;

    @Size(max = 1000, message = "关闭原因不能超过1000字符")
    private String closeReason;
}
