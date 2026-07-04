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

    private Long conversationId;
    /** user / assistant / tool / agent / system */
    private String role;
    private String content;
    private String intent;
    private String sentiment;
    private BigDecimal sentimentScore;
    private String toolName;
    private String toolArgs;
    private String toolResult;
    private LocalDateTime createdAt;
}
