package com.portfolio.csagent.agent.tool.impl;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.portfolio.csagent.agent.tool.AgentTool;
import com.portfolio.csagent.agent.tool.ToolExecutionContext;
import com.portfolio.csagent.agent.tool.ToolNames;
import com.portfolio.csagent.agent.tool.ToolResult;
import com.portfolio.csagent.agent.tool.ToolSupport;
import com.portfolio.csagent.entity.Ticket;
import com.portfolio.csagent.llm.ToolSpec;
import com.portfolio.csagent.mapper.TicketMapper;
import org.springframework.stereotype.Component;

@Component
public class QueryTicketTool implements AgentTool {
    private final TicketMapper ticketMapper;

    public QueryTicketTool(TicketMapper ticketMapper) {
        this.ticketMapper = ticketMapper;
    }

    @Override public String name() { return ToolNames.QUERY_TICKET; }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(name(), "查询当前客户的工单进度；不提供工单号时查询本会话最新工单",
                ToolSupport.schema(Map.of("ticket_no", ToolSupport.prop("string", "工单号，可选")), List.of()));
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, Map<String, Object> args) {
        String ticketNo = ToolSupport.str(args, "ticket_no");
        var query = Wrappers.<Ticket>lambdaQuery()
                .eq(Ticket::getTenantId, context.actor().tenantId())
                .eq(Ticket::getCustomer, context.conversation().getCustomerUsername());
        if (ticketNo != null) {
            query.eq(Ticket::getTicketNo, ticketNo);
        } else {
            query.eq(Ticket::getConversationId, context.conversation().getId()).orderByDesc(Ticket::getId);
        }
        Ticket ticket = ticketMapper.selectOne(query.last("limit 1"));
        if (ticket == null) {
            return ToolResult.fail("未找到当前客户的对应工单。请核对工单号，或转人工协助查询。");
        }
        return ToolResult.ok(String.format("工单 %s 当前状态：%s，优先级：%s，处理人：%s，SLA 截止：%s。",
                ticket.getTicketNo(), ticket.getStatus(), ticket.getPriority(),
                ticket.getAssignee() == null ? "待认领" : ticket.getAssignee(), ticket.getSlaDueAt()), ticket);
    }
}
