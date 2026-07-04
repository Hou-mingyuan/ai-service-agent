package com.portfolio.csagent.agent.tool.impl;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.portfolio.csagent.agent.tool.AgentTool;
import com.portfolio.csagent.agent.tool.ToolNames;
import com.portfolio.csagent.agent.tool.ToolResult;
import com.portfolio.csagent.agent.tool.ToolSupport;
import com.portfolio.csagent.entity.Shipment;
import com.portfolio.csagent.llm.ToolSpec;
import com.portfolio.csagent.mapper.ShipmentMapper;
import org.springframework.stereotype.Component;

@Component
public class LogisticsQueryTool implements AgentTool {

    private final ShipmentMapper shipmentMapper;

    public LogisticsQueryTool(ShipmentMapper shipmentMapper) {
        this.shipmentMapper = shipmentMapper;
    }

    @Override
    public String name() {
        return ToolNames.QUERY_LOGISTICS;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(name(), "根据订单号或运单号查询物流轨迹与配送状态",
                ToolSupport.schema(
                        Map.of(
                                "order_no", ToolSupport.prop("string", "订单号"),
                                "tracking_no", ToolSupport.prop("string", "运单号（可选）")),
                        List.of("order_no")));
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String orderNo = ToolSupport.str(args, "order_no");
        String trackingNo = ToolSupport.str(args, "tracking_no");
        if (orderNo == null && trackingNo == null) {
            return ToolResult.fail("请提供订单号或运单号以查询物流。");
        }
        Shipment s = shipmentMapper.selectOne(Wrappers.<Shipment>lambdaQuery()
                .eq(orderNo != null, Shipment::getOrderNo, orderNo)
                .eq(trackingNo != null, Shipment::getTrackingNo, trackingNo)
                .last("limit 1"));
        if (s == null) {
            return ToolResult.fail("未查询到订单「" + (orderNo != null ? orderNo : trackingNo)
                    + "」的物流信息，可能尚未发货。");
        }
        String summary = String.format("订单 %s 物流：%s，运单号 %s，当前状态：%s，最新位置：%s。",
                s.getOrderNo(), s.getCarrier(), s.getTrackingNo(), statusCn(s.getStatus()),
                s.getLastLocation());
        return ToolResult.ok(summary, s);
    }

    private String statusCn(String s) {
        if (s == null) {
            return "未知";
        }
        return switch (s) {
            case "PENDING" -> "待揽收";
            case "IN_TRANSIT" -> "运输中";
            case "OUT_FOR_DELIVERY" -> "派送中";
            case "SIGNED" -> "已签收";
            default -> s;
        };
    }
}
