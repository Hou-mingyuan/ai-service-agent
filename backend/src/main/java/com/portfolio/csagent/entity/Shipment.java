package com.portfolio.csagent.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("shipment")
public class Shipment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tenantId;
    private String ownerUsername;
    private String orderNo;
    private String trackingNo;
    private String carrier;
    /** PENDING / IN_TRANSIT / OUT_FOR_DELIVERY / SIGNED */
    private String status;
    private String lastLocation;
    private String adapterSource;
    private LocalDateTime updatedAt;
}
