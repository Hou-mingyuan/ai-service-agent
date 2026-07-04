package com.portfolio.csagent.agent.tool.impl;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.portfolio.csagent.agent.tool.AgentTool;
import com.portfolio.csagent.agent.tool.ToolNames;
import com.portfolio.csagent.agent.tool.ToolResult;
import com.portfolio.csagent.agent.tool.ToolSupport;
import com.portfolio.csagent.entity.OrderInfo;
import com.portfolio.csagent.llm.ToolSpec;
import com.portfolio.csagent.mapper.OrderInfoMapper;
import org.springframework.stereotype.Component;

@Component
public class OrderQueryTool implements AgentTool {

    private final OrderInfoMapper orderMapper;

    public OrderQueryTool(OrderInfoMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public String name() {
        return ToolNames.QUERY_ORDER;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(name(), "根据订单号查询订单详情（商品、金额、状态、收货地址）",
                ToolSupport.schema(
                        Map.of("order_no", ToolSupport.prop("string", "订单号，例如 123")),
                        List.of("order_no")));
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String orderNo = ToolSupport.str(args, "order_no");
        if (orderNo == null) {
            return ToolResult.fail("请提供需要查询的订单号。");
        }
        OrderInfo o = orderMapper.selectOne(
                Wrappers.<OrderInfo>lambdaQuery().eq(OrderInfo::getOrderNo, orderNo).last("limit 1"));
        if (o == null) {
            return ToolResult.fail("未查询到订单号「" + orderNo + "」，请核对后重试。");
        }
        String summary = String.format("订单 %s：%s，金额 ¥%s，状态：%s，收货地址：%s。",
                o.getOrderNo(), o.getProduct(), o.getAmount(), statusCn(o.getStatus()), o.getAddress());
        return ToolResult.ok(summary, o);
    }

    private String statusCn(String s) {
        if (s == null) {
            return "未知";
        }
        return switch (s) {
            case "PAID" -> "已支付待发货";
            case "SHIPPED" -> "已发货";
            case "DELIVERED" -> "已签收";
            case "REFUNDING" -> "退款处理中";
            case "CANCELLED" -> "已取消";
            default -> s;
        };
    }
}
