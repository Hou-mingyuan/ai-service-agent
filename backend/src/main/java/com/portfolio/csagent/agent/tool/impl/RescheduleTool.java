package com.portfolio.csagent.agent.tool.impl;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import com.portfolio.csagent.adapter.business.BusinessSystemAdapter;
import com.portfolio.csagent.agent.tool.AgentTool;
import com.portfolio.csagent.agent.tool.ToolExecutionContext;
import com.portfolio.csagent.agent.tool.ToolNames;
import com.portfolio.csagent.agent.tool.ToolResult;
import com.portfolio.csagent.agent.tool.ToolSupport;
import com.portfolio.csagent.common.BizException;
import com.portfolio.csagent.llm.ToolSpec;
import com.portfolio.csagent.security.Permission;
import com.portfolio.csagent.service.TicketService;
import org.springframework.stereotype.Component;

@Component
public class RescheduleTool implements AgentTool {
    private final BusinessSystemAdapter adapter;
    private final TicketService ticketService;

    public RescheduleTool(BusinessSystemAdapter adapter, TicketService ticketService) {
        this.adapter = adapter;
        this.ticketService = ticketService;
    }

    @Override public String name() { return ToolNames.RESCHEDULE; }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(name(), "申请订单配送或保单续费/回访改期；必须由客户确认后执行",
                ToolSupport.schema(Map.of(
                        "order_no", ToolSupport.prop("string", "订单号，与保单号二选一"),
                        "policy_no", ToolSupport.prop("string", "保单号，与订单号二选一"),
                        "new_date", ToolSupport.dateProp("新日期，格式 YYYY-MM-DD")), List.of("new_date")));
    }

    @Override public boolean sensitive() { return true; }

    @Override public String requiredPermission() { return Permission.TOOL_SENSITIVE; }

    @Override public String adapterSource() { return adapter.source(); }

    @Override
    public void validate(ToolExecutionContext context, Map<String, Object> args) {
        String orderNo = ToolSupport.str(args, "order_no");
        String policyNo = ToolSupport.str(args, "policy_no");
        if ((orderNo == null) == (policyNo == null)) {
            throw new BizException(400, "订单号和保单号必须且只能提供一个");
        }
        String newDate = ToolSupport.str(args, "new_date");
        try {
            LocalDate parsed = LocalDate.parse(newDate);
            if (parsed.isBefore(LocalDate.now())) {
                throw new BizException(400, "改期日期不能早于今天");
            }
        } catch (DateTimeParseException exception) {
            throw new BizException(400, "改期日期必须使用 YYYY-MM-DD 格式");
        }
        String tenant = context.actor().tenantId();
        String owner = context.conversation().getCustomerUsername();
        boolean exists = orderNo != null
                ? adapter.findOrder(tenant, owner, orderNo).isPresent()
                : adapter.findPolicy(tenant, owner, policyNo).isPresent();
        if (!exists) {
            throw new BizException(404, "未找到当前客户名下的业务单据");
        }
    }

    @Override
    public String confirmationSummary(ToolExecutionContext context, Map<String, Object> args) {
        String number = ToolSupport.str(args, "order_no") != null
                ? "订单 " + ToolSupport.str(args, "order_no")
                : "保单 " + ToolSupport.str(args, "policy_no");
        return "确认将" + number + "改期到 " + ToolSupport.str(args, "new_date") + "？确认后会写入业务系统并创建跟进工单。";
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, Map<String, Object> args) {
        String orderNo = ToolSupport.str(args, "order_no");
        String policyNo = ToolSupport.str(args, "policy_no");
        String newDate = ToolSupport.str(args, "new_date");
        BusinessSystemAdapter.RescheduleData changed = adapter.reschedule(context.actor().tenantId(),
                context.conversation().getCustomerUsername(), orderNo, policyNo, newDate,
                context.executionKey());
        String number = orderNo != null ? orderNo : policyNo;
        TicketService.CreateResult ticket = ticketService.createIdempotent(new TicketService.CreateCommand(
                context.actor().tenantId(), context.conversation().getId(), "OTHER",
                "改期跟进：" + number + " -> " + newDate,
                "客户已确认改期，请坐席核对业务系统处理结果。", "MEDIUM", "AGENT",
                context.conversation().getCustomerUsername(), "reschedule-ticket:" + context.executionKey(),
                context.actor().username(), context.actor().role().getId()));
        String outcome = "CONFIRMED".equals(changed.status()) ? "已在业务系统生效" : "已提交业务系统处理";
        return ToolResult.ok("改期请求" + outcome + "，并创建跟进工单 " + ticket.ticket().getTicketNo() + "。",
                Map.of("business", changed, "ticketNo", ticket.ticket().getTicketNo(),
                        "dataSource", adapter.source()));
    }
}
