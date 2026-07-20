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
public class OrderQueryTool implements AgentTool {
    private final BusinessSystemAdapter adapter;

    public OrderQueryTool(BusinessSystemAdapter adapter) {
        this.adapter = adapter;
    }

    @Override public String name() { return ToolNames.QUERY_ORDER; }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(name(), "按当前客户权限查询订单状态、商品、金额和脱敏地址",
                ToolSupport.schema(Map.of("order_no", ToolSupport.prop("string", "订单号")),
                        List.of("order_no")));
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, Map<String, Object> args) {
        String orderNo = ToolSupport.str(args, "order_no");
        return adapter.findOrder(context.actor().tenantId(), context.conversation().getCustomerUsername(), orderNo)
                .map(order -> ToolResult.ok(String.format(
                        "订单 %s：%s，金额 ¥%s，状态：%s，收货区域：%s。",
                        order.orderNo(), order.product(), order.amount(), order.status(), order.maskedAddress()), order))
                .orElseGet(() -> ToolResult.fail("未找到该客户名下的订单，请核对订单号。"));
    }

    @Override public String adapterSource() { return adapter.source(); }
}
