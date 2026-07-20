package com.portfolio.csagent.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("realtime_event")
public class RealtimeEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String tenantId;
    private String audienceType;
    private String audienceId;
    private String type;
    private String payloadJson;
    private LocalDateTime createdAt;
}
