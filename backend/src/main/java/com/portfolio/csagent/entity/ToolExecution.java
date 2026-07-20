package com.portfolio.csagent.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@TableName("tool_execution")
public class ToolExecution {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String tenantId;
    private String executionKey;
    private Long conversationId;
    private String customerUsername;
    private String toolName;
    private String argumentsJson;
    private String resultJson;
    private String summary;
    private String status;
    @JsonProperty("sensitive")
    private Integer isSensitive;
    private String adapterSource;
    private String confirmedBy;
    private LocalDateTime confirmedAt;
    private Long durationMs;
    private String errorCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
