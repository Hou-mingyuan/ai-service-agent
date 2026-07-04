package com.portfolio.csagent.agent.tool.impl;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.portfolio.csagent.agent.tool.AgentTool;
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

    @Override
    public String name() {
        return ToolNames.QUERY_TICKET;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(name(), "查询工单处理进度。可按工单号查询，或查询本次会话最近的工单。",
                ToolSupport.schema(
                        Map.of("ticket_no", ToolSupport.prop("string", "工单号，例如 TK20260704-0001（可选）")),
                        List.of()));
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String ticketNo = ToolSupport.str(args, "ticket_no");
        Long conversationId = parseLong(ToolSupport.str(args, "conversation_id"));
        Ticket t = null;
        if (ticketNo != null) {
            t = ticketMapper.selectOne(
                    Wrappers.<Ticket>lambdaQuery().eq(Ticket::getTicketNo, ticketNo).last("limit 1"));
        } else if (conversationId != null) {
            t = ticketMapper.selectOne(Wrappers.<Ticket>lambdaQuery()
                    .eq(Ticket::getConversationId, conversationId)
                    .orderByDesc(Ticket::getId).last("limit 1"));
        }
        if (t == null) {
            return ToolResult.fail("未找到对应的工单，请提供正确的工单号。");
        }
        String summary = String.format("工单 %s（%s）当前状态：%s，优先级：%s，处理人：%s。",
                t.getTicketNo(), t.getTitle(), statusCn(t.getStatus()), t.getPriority(),
                t.getAssignee() == null ? "待分配" : t.getAssignee());
        return ToolResult.ok(summary, t);
    }

    private String statusCn(String s) {
        if (s == null) {
            return "未知";
        }
        return switch (s) {
            case "OPEN" -> "待处理";
            case "IN_PROGRESS" -> "处理中";
            case "PENDING" -> "挂起等待";
            case "RESOLVED" -> "已解决";
            case "CLOSED" -> "已关闭";
            default -> s;
        };
    }

    private Long parseLong(String s) {
        try {
            return s == null ? null : Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
