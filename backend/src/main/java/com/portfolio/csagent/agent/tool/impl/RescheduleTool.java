package com.portfolio.csagent.agent.tool.impl;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.portfolio.csagent.agent.tool.AgentTool;
import com.portfolio.csagent.agent.tool.ToolNames;
import com.portfolio.csagent.agent.tool.ToolResult;
import com.portfolio.csagent.agent.tool.ToolSupport;
import com.portfolio.csagent.entity.OrderInfo;
import com.portfolio.csagent.entity.Policy;
import com.portfolio.csagent.llm.ToolSpec;
import com.portfolio.csagent.mapper.OrderInfoMapper;
import com.portfolio.csagent.mapper.PolicyMapper;
import com.portfolio.csagent.service.TicketService;
import org.springframework.stereotype.Component;

/** 预约改期：配送改期 / 保单续费改期，受理后登记工单由专员跟进确认。 */
@Component
public class RescheduleTool implements AgentTool {

    private final OrderInfoMapper orderMapper;
    private final PolicyMapper policyMapper;
    private final TicketService ticketService;

    public RescheduleTool(OrderInfoMapper orderMapper, PolicyMapper policyMapper,
                          TicketService ticketService) {
        this.orderMapper = orderMapper;
        this.policyMapper = policyMapper;
        this.ticketService = ticketService;
    }

    @Override
    public String name() {
        return ToolNames.RESCHEDULE;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(name(),
                "受理预约改期：可用于修改订单配送时间或保单续费/回访时间。",
                ToolSupport.schema(
                        Map.of(
                                "order_no", ToolSupport.prop("string", "订单号（配送改期时提供）"),
                                "policy_no", ToolSupport.prop("string", "保单号（保单改期时提供）"),
                                "new_date", ToolSupport.prop("string", "期望的新日期，例如 2026-07-10")),
                        List.of("new_date")));
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String newDate = ToolSupport.str(args, "new_date");
        if (newDate == null) {
            return ToolResult.fail("请告知您希望改到的具体日期（例如 2026-07-10）。");
        }
        String orderNo = ToolSupport.str(args, "order_no");
        String policyNo = ToolSupport.str(args, "policy_no");
        Long conversationId = parseLong(ToolSupport.str(args, "conversation_id"));
        String customer = ToolSupport.str(args, "customer");

        String scope;
        String bizNo = orderNo != null ? orderNo : policyNo;
        if (orderNo != null) {
            OrderInfo o = orderMapper.selectOne(
                    Wrappers.<OrderInfo>lambdaQuery().eq(OrderInfo::getOrderNo, orderNo).last("limit 1"));
            if (o == null) {
                return ToolResult.fail("未查询到订单「" + orderNo + "」，无法办理配送改期。");
            }
            scope = "订单 " + orderNo + " 配送改期";
        } else if (policyNo != null) {
            Policy p = policyMapper.selectOne(
                    Wrappers.<Policy>lambdaQuery().eq(Policy::getPolicyNo, policyNo).last("limit 1"));
            if (p == null) {
                return ToolResult.fail("未查询到保单「" + policyNo + "」，无法办理改期。");
            }
            p.setNextPaymentDate(newDate);
            policyMapper.updateById(p);
            scope = "保单 " + policyNo + " 续费/回访改期";
        } else {
            return ToolResult.fail("请提供订单号或保单号以办理改期。");
        }

        ticketService.create(conversationId, "OTHER", scope + "至 " + newDate,
                "客户申请将「" + scope + "」调整为 " + newDate + "，请专员联系确认。",
                "MEDIUM", "AGENT", customer);
        String summary = String.format("已受理%s，目标日期：%s。已登记工单，客服专员将尽快与您电话确认。",
                scope, newDate);
        return ToolResult.ok(summary, Map.of("bizNo", bizNo == null ? "" : bizNo, "newDate", newDate));
    }

    private Long parseLong(String s) {
        try {
            return s == null ? null : Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
