package com.portfolio.csagent.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("message")
public class Message {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tenantId;
    private Long conversationId;
    private String clientMessageId;
    /** user / assistant / tool / agent / system */
    private String role;
    private String senderUsername;
    private String senderName;
    private String content;
    private String deliveryStatus;
    private LocalDateTime readAt;
    private String intent;
    private BigDecimal intentConfidence;
    private String sentiment;
    private BigDecimal sentimentScore;
    private String classificationSource;
    private String toolName;
    private String toolArgs;
    private String toolResult;
    private LocalDateTime createdAt;
}
