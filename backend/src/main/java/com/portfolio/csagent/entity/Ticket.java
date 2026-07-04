package com.portfolio.csagent.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("ticket")
public class Ticket {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ticketNo;
    private Long conversationId;
    /** ORDER / LOGISTICS / POLICY / REFUND / COMPLAINT / OTHER */
    private String category;
    private String title;
    private String description;
    /** LOW / MEDIUM / HIGH / URGENT */
    private String priority;
    /** OPEN / IN_PROGRESS / PENDING / RESOLVED / CLOSED */
    private String status;
    private String assignee;
    /** AGENT(机器人自动) / HUMAN / SYSTEM */
    private String source;
    private String customer;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime closedAt;
}
