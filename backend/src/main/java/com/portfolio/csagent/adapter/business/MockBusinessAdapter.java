package com.portfolio.csagent.adapter.business;

import java.util.Optional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.portfolio.csagent.entity.OrderInfo;
import com.portfolio.csagent.entity.Policy;
import com.portfolio.csagent.entity.Shipment;
import com.portfolio.csagent.mapper.OrderInfoMapper;
import com.portfolio.csagent.mapper.PolicyMapper;
import com.portfolio.csagent.mapper.ShipmentMapper;
import com.portfolio.csagent.security.RedactionService;

public class MockBusinessAdapter implements BusinessSystemAdapter {
    private final OrderInfoMapper orderMapper;
    private final ShipmentMapper shipmentMapper;
    private final PolicyMapper policyMapper;
    private final RedactionService redactionService;

    public MockBusinessAdapter(OrderInfoMapper orderMapper, ShipmentMapper shipmentMapper,
                               PolicyMapper policyMapper, RedactionService redactionService) {
        this.orderMapper = orderMapper;
        this.shipmentMapper = shipmentMapper;
        this.policyMapper = policyMapper;
        this.redactionService = redactionService;
    }

    @Override
    public String source() {
        return "mock";
    }

    @Override
    public boolean mock() {
        return true;
    }

    @Override
    public AdapterHealth health() {
        return new AdapterHealth(source(), "UP", true, "内置虚构业务数据，仅用于演示和测试");
    }

    @Override
    public Optional<OrderData> findOrder(String tenantId, String ownerUsername, String orderNo) {
        OrderInfo order = orderMapper.selectOne(Wrappers.<OrderInfo>lambdaQuery()
                .eq(OrderInfo::getTenantId, tenantId)
                .eq(OrderInfo::getOwnerUsername, ownerUsername)
                .eq(OrderInfo::getOrderNo, orderNo)
                .last("limit 1"));
        return Optional.ofNullable(order).map(value -> new OrderData(value.getOrderNo(), value.getProduct(),
                value.getAmount(), value.getStatus(), redactionService.maskAddress(value.getAddress()), source()));
    }

    @Override
    public Optional<ShipmentData> findShipment(String tenantId, String ownerUsername,
                                               String orderNo, String trackingNo) {
        Shipment shipment = shipmentMapper.selectOne(Wrappers.<Shipment>lambdaQuery()
                .eq(Shipment::getTenantId, tenantId)
                .eq(Shipment::getOwnerUsername, ownerUsername)
                .eq(orderNo != null, Shipment::getOrderNo, orderNo)
                .eq(trackingNo != null, Shipment::getTrackingNo, trackingNo)
                .last("limit 1"));
        return Optional.ofNullable(shipment).map(value -> new ShipmentData(value.getOrderNo(),
                value.getTrackingNo(), value.getCarrier(), value.getStatus(), value.getLastLocation(), source()));
    }

    @Override
    public Optional<PolicyData> findPolicy(String tenantId, String ownerUsername, String policyNo) {
        Policy policy = policyMapper.selectOne(Wrappers.<Policy>lambdaQuery()
                .eq(Policy::getTenantId, tenantId)
                .eq(Policy::getOwnerUsername, ownerUsername)
                .eq(Policy::getPolicyNo, policyNo)
                .last("limit 1"));
        return Optional.ofNullable(policy).map(value -> new PolicyData(value.getPolicyNo(),
                redactionService.maskName(value.getHolder()), value.getProduct(), value.getPremium(),
                value.getStatus(), value.getEffectiveDate(), value.getExpireDate(),
                value.getNextPaymentDate(), source()));
    }

    @Override
    public RescheduleData reschedule(String tenantId, String ownerUsername, String orderNo,
                                     String policyNo, String newDate, String idempotencyKey) {
        if (orderNo != null) {
            findOrder(tenantId, ownerUsername, orderNo).orElseThrow();
            return new RescheduleData(orderNo, newDate, "REQUESTED", source());
        }
        Policy policy = policyMapper.selectOne(Wrappers.<Policy>lambdaQuery()
                .eq(Policy::getTenantId, tenantId)
                .eq(Policy::getOwnerUsername, ownerUsername)
                .eq(Policy::getPolicyNo, policyNo)
                .last("limit 1"));
        if (policy == null) {
            throw new java.util.NoSuchElementException("policy not found");
        }
        policy.setNextPaymentDate(newDate);
        policyMapper.updateById(policy);
        return new RescheduleData(policyNo, newDate, "CONFIRMED", source());
    }
}
