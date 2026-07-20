package com.portfolio.csagent.agent.tool.impl;

import java.util.List;
import java.util.Map;

import com.portfolio.csagent.adapter.business.BusinessSystemAdapter;
import com.portfolio.csagent.agent.tool.AgentTool;
import com.portfolio.csagent.agent.tool.ToolExecutionContext;
import com.portfolio.csagent.agent.tool.ToolNames;
import com.portfolio.csagent.agent.tool.ToolResult;
import com.portfolio.csagent.agent.tool.ToolSupport;
import com.portfolio.csagent.llm.ToolSpec;
import org.springframework.stereotype.Component;

@Component
public class LogisticsQueryTool implements AgentTool {
    private final BusinessSystemAdapter adapter;

    public LogisticsQueryTool(BusinessSystemAdapter adapter) {
        this.adapter = adapter;
    }

    @Override public String name() { return ToolNames.QUERY_LOGISTICS; }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(name(), "按当前客户权限查询物流轨迹",
                ToolSupport.schema(Map.of(
                        "order_no", ToolSupport.prop("string", "订单号，可与运单号二选一"),
                        "tracking_no", ToolSupport.prop("string", "运单号，可与订单号二选一")), List.of()));
    }

    @Override
    public void validate(ToolExecutionContext context, Map<String, Object> args) {
        if (ToolSupport.str(args, "order_no") == null && ToolSupport.str(args, "tracking_no") == null) {
            throw new com.portfolio.csagent.common.BizException(400, "请提供订单号或运单号");
        }
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, Map<String, Object> args) {
        String orderNo = ToolSupport.str(args, "order_no");
        String trackingNo = ToolSupport.str(args, "tracking_no");
        return adapter.findShipment(context.actor().tenantId(), context.conversation().getCustomerUsername(),
                        orderNo, trackingNo)
                .map(shipment -> ToolResult.ok(String.format(
                        "订单 %s 由 %s 承运，运单号 %s，状态：%s，最新位置：%s。",
                        shipment.orderNo(), shipment.carrier(), shipment.trackingNo(),
                        shipment.status(), shipment.lastLocation()), shipment))
                .orElseGet(() -> ToolResult.fail("未找到该客户名下的物流记录。"));
    }

    @Override public String adapterSource() { return adapter.source(); }
}
