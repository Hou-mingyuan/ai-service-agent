package com.portfolio.csagent.agent.tool.impl;

import java.util.List;
import java.util.Map;

import com.portfolio.csagent.agent.tool.AgentTool;
import com.portfolio.csagent.agent.tool.ToolExecutionContext;
import com.portfolio.csagent.agent.tool.ToolNames;
import com.portfolio.csagent.agent.tool.ToolResult;
import com.portfolio.csagent.agent.tool.ToolSupport;
import com.portfolio.csagent.llm.ToolSpec;
import com.portfolio.csagent.service.TicketService;
import org.springframework.stereotype.Component;

@Component
public class CreateTicketTool implements AgentTool {
    private final TicketService ticketService;

    public CreateTicketTool(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Override public String name() { return ToolNames.CREATE_TICKET; }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(name(), "为当前会话创建需人工跟进的售后或咨询工单",
                ToolSupport.schema(Map.of(
                        "category", ToolSupport.enumProp("工单类别",
                                List.of("ORDER", "LOGISTICS", "POLICY", "REFUND", "COMPLAINT", "OTHER")),
                        "title", ToolSupport.prop("string", "工单标题"),
                        "description", ToolSupport.prop("string", "问题详情"),
                        "priority", ToolSupport.enumProp("优先级",
                                List.of("LOW", "MEDIUM", "HIGH", "URGENT"))), List.of("title")));
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, Map<String, Object> args) {
        String category = ToolSupport.str(args, "category");
        String title = ToolSupport.str(args, "title");
        String description = ToolSupport.str(args, "description");
        String priority = ToolSupport.str(args, "priority");
        String key = "tool-ticket:" + context.executionKey().substring("tool:".length());
        TicketService.CreateResult result = ticketService.createIdempotent(new TicketService.CreateCommand(
                context.actor().tenantId(), context.conversation().getId(), category, title,
                description == null ? title : description, priority, "AGENT",
                context.conversation().getCustomerUsername(), key,
                context.actor().username(), context.actor().role().getId()));
        return ToolResult.ok(String.format("已创建工单 %s（%s / %s），等待坐席认领。",
                result.ticket().getTicketNo(), result.ticket().getCategory(), result.ticket().getPriority()),
                Map.of("ticketNo", result.ticket().getTicketNo(), "status", result.ticket().getStatus(),
                        "priority", result.ticket().getPriority(), "replayed", result.replayed()));
    }
}
