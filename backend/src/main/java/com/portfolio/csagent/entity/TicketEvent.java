package com.portfolio.csagent.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("ticket_event")
public class TicketEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tenantId;
    private Long ticketId;
    private String eventType;
    private String eventKey;
    private String fromStatus;
    private String toStatus;
    private String note;
    private String operator;
    private String actorRole;
    private String metadataJson;
    private LocalDateTime createdAt;
}
