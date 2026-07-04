package com.portfolio.csagent.agent.tool.impl;

import java.util.List;
import java.util.Map;

import com.portfolio.csagent.agent.tool.AgentTool;
import com.portfolio.csagent.agent.tool.ToolNames;
import com.portfolio.csagent.agent.tool.ToolResult;
import com.portfolio.csagent.agent.tool.ToolSupport;
import com.portfolio.csagent.entity.Ticket;
import com.portfolio.csagent.llm.ToolSpec;
import com.portfolio.csagent.service.TicketService;
import org.springframework.stereotype.Component;

@Component
public class CreateTicketTool implements AgentTool {

    private final TicketService ticketService;

    public CreateTicketTool(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Override
    public String name() {
        return ToolNames.CREATE_TICKET;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(name(),
                "为客户创建售后/咨询工单。适用于需要人工跟进、投诉、退款等无法即时解决的问题。",
                ToolSupport.schema(
                        Map.of(
                                "category", ToolSupport.prop("string",
                                        "工单类别：ORDER/LOGISTICS/POLICY/REFUND/COMPLAINT/OTHER"),
                                "title", ToolSupport.prop("string", "工单标题（一句话概括问题）"),
                                "description", ToolSupport.prop("string", "问题详情"),
                                "priority", ToolSupport.prop("string",
                                        "优先级：LOW/MEDIUM/HIGH/URGENT（可选）")),
                        List.of("title")));
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String title = ToolSupport.str(args, "title");
        if (title == null) {
            return ToolResult.fail("请简要描述需要登记的问题，以便为您创建工单。");
        }
        String category = ToolSupport.str(args, "category");
        String description = ToolSupport.str(args, "description");
        String priority = ToolSupport.str(args, "priority");
        if (priority == null) {
            priority = "COMPLAINT".equalsIgnoreCase(category) ? "HIGH" : "MEDIUM";
        }
        Long conversationId = parseLong(ToolSupport.str(args, "conversation_id"));
        String customer = ToolSupport.str(args, "customer");

        Ticket t = ticketService.create(conversationId, category, title,
                description == null ? title : description, priority, "AGENT", customer);
        String summary = String.format("已为您创建工单 %s（类别：%s，优先级：%s），我们会尽快跟进处理。",
                t.getTicketNo(), t.getCategory(), t.getPriority());
        return ToolResult.ok(summary, t);
    }

    private Long parseLong(String s) {
        try {
            return s == null ? null : Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
