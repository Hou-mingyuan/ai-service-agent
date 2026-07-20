package com.portfolio.csagent.controller;

import java.util.List;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.portfolio.csagent.adapter.business.BusinessSystemAdapter;
import com.portfolio.csagent.common.ApiResponse;
import com.portfolio.csagent.entity.OrderInfo;
import com.portfolio.csagent.entity.Policy;
import com.portfolio.csagent.mapper.OrderInfoMapper;
import com.portfolio.csagent.mapper.PolicyMapper;
import com.portfolio.csagent.security.AuthenticatedUser;
import com.portfolio.csagent.security.CurrentActor;
import com.portfolio.csagent.security.Permission;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {
    private final OrderInfoMapper orderMapper;
    private final PolicyMapper policyMapper;
    private final BusinessSystemAdapter adapter;
    private final CurrentActor currentActor;

    public CatalogController(OrderInfoMapper orderMapper, PolicyMapper policyMapper,
                             BusinessSystemAdapter adapter, CurrentActor currentActor) {
        this.orderMapper = orderMapper;
        this.policyMapper = policyMapper;
        this.adapter = adapter;
        this.currentActor = currentActor;
    }

    @GetMapping("/orders")
    @PreAuthorize("hasAuthority('" + Permission.TOOL_READ + "')")
    public ApiResponse<List<BusinessSystemAdapter.OrderData>> orders() {
        AuthenticatedUser actor = currentActor.require();
        List<String> numbers = orderMapper.selectList(Wrappers.<OrderInfo>lambdaQuery()
                        .eq(OrderInfo::getTenantId, actor.tenantId())
                        .eq(OrderInfo::getOwnerUsername, actor.username())
                        .orderByDesc(OrderInfo::getId))
                .stream().map(OrderInfo::getOrderNo).toList();
        return ApiResponse.ok(numbers.stream()
                .map(number -> adapter.findOrder(actor.tenantId(), actor.username(), number).orElse(null))
                .filter(java.util.Objects::nonNull).toList());
    }

    @GetMapping("/policies")
    @PreAuthorize("hasAuthority('" + Permission.TOOL_READ + "')")
    public ApiResponse<List<BusinessSystemAdapter.PolicyData>> policies() {
        AuthenticatedUser actor = currentActor.require();
        List<String> numbers = policyMapper.selectList(Wrappers.<Policy>lambdaQuery()
                        .eq(Policy::getTenantId, actor.tenantId())
                        .eq(Policy::getOwnerUsername, actor.username())
                        .orderByDesc(Policy::getId))
                .stream().map(Policy::getPolicyNo).toList();
        return ApiResponse.ok(numbers.stream()
                .map(number -> adapter.findPolicy(actor.tenantId(), actor.username(), number).orElse(null))
                .filter(java.util.Objects::nonNull).toList());
    }
}
