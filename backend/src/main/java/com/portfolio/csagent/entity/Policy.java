package com.portfolio.csagent.entity;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("policy")
public class Policy {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tenantId;
    private String ownerUsername;
    private String policyNo;
    private String holder;
    private String product;
    private BigDecimal premium;
    /** ACTIVE / LAPSED / EXPIRED / CANCELLED */
    private String status;
    private String effectiveDate;
    private String expireDate;
    private String nextPaymentDate;
    private String adapterSource;
}
