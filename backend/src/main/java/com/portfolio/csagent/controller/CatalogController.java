package com.portfolio.csagent.controller;

import java.util.List;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.portfolio.csagent.common.ApiResponse;
import com.portfolio.csagent.entity.OrderInfo;
import com.portfolio.csagent.entity.Policy;
import com.portfolio.csagent.mapper.OrderInfoMapper;
import com.portfolio.csagent.mapper.PolicyMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 示例业务数据浏览（便于在前端演示「有哪些订单/保单可查」）。 */
@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final OrderInfoMapper orderMapper;
    private final PolicyMapper policyMapper;

    public CatalogController(OrderInfoMapper orderMapper, PolicyMapper policyMapper) {
        this.orderMapper = orderMapper;
        this.policyMapper = policyMapper;
    }

    @GetMapping("/orders")
    public ApiResponse<List<OrderInfo>> orders() {
        return ApiResponse.ok(orderMapper.selectList(
                Wrappers.<OrderInfo>lambdaQuery().orderByDesc(OrderInfo::getId)));
    }

    @GetMapping("/policies")
    public ApiResponse<List<Policy>> policies() {
        return ApiResponse.ok(policyMapper.selectList(
                Wrappers.<Policy>lambdaQuery().orderByDesc(Policy::getId)));
    }
}
