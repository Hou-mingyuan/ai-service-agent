package com.portfolio.csagent.adapter.business;

import java.math.BigDecimal;
import java.util.Optional;

public interface BusinessSystemAdapter {
    String source();

    boolean mock();

    AdapterHealth health();

    Optional<OrderData> findOrder(String tenantId, String ownerUsername, String orderNo);

    Optional<ShipmentData> findShipment(String tenantId, String ownerUsername,
                                        String orderNo, String trackingNo);

    Optional<PolicyData> findPolicy(String tenantId, String ownerUsername, String policyNo);

    RescheduleData reschedule(String tenantId, String ownerUsername, String orderNo,
                              String policyNo, String newDate, String idempotencyKey);

    record AdapterHealth(String source, String status, boolean mock, String detail) {
    }

    record OrderData(String orderNo, String product, BigDecimal amount, String status,
                     String maskedAddress, String dataSource) {
    }

    record ShipmentData(String orderNo, String trackingNo, String carrier, String status,
                        String lastLocation, String dataSource) {
    }

    record PolicyData(String policyNo, String maskedHolder, String product, BigDecimal premium,
                      String status, String effectiveDate, String expireDate,
                      String nextPaymentDate, String dataSource) {
    }

    record RescheduleData(String businessNo, String newDate, String status, String dataSource) {
    }
}
