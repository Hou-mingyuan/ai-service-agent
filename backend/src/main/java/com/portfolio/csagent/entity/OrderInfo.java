package com.portfolio.csagent.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("order_info")
public class OrderInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;
    private String customer;
    private String product;
    private BigDecimal amount;
    /** PAID / SHIPPED / DELIVERED / REFUNDING / CANCELLED */
    private String status;
    private String address;
    private LocalDateTime createdAt;
}
