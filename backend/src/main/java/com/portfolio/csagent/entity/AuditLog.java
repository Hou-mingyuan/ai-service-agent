package com.portfolio.csagent.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("audit_log")
public class AuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String tenantId;
    private String requestId;
    private String actorUsername;
    private String actorRole;
    private String action;
    private String resourceType;
    private String resourceId;
    private String outcome;
    private String idempotencyKey;
    private String detailsJson;
    private LocalDateTime createdAt;
}
