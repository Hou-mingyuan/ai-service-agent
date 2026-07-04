package com.portfolio.csagent.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("conversation")
public class Conversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionKey;
    private String userName;
    private String channel;
    /** BOT / HUMAN_PENDING / HUMAN / CLOSED */
    private String status;
    private String lastIntent;
    private String lastSentiment;
    private Integer resolved;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
