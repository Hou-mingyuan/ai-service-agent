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
public class PolicyQueryTool implements AgentTool {
    private final BusinessSystemAdapter adapter;

    public PolicyQueryTool(BusinessSystemAdapter adapter) {
        this.adapter = adapter;
    }

    @Override public String name() { return ToolNames.QUERY_POLICY; }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(name(), "按当前客户权限查询保单信息，投保人姓名会脱敏",
                ToolSupport.schema(Map.of("policy_no", ToolSupport.prop("string", "保单号")),
                        List.of("policy_no")));
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, Map<String, Object> args) {
        String policyNo = ToolSupport.str(args, "policy_no");
        return adapter.findPolicy(context.actor().tenantId(), context.conversation().getCustomerUsername(), policyNo)
                .map(policy -> ToolResult.ok(String.format(
                        "保单 %s：投保人 %s，险种 %s，状态：%s，保障期 %s 至 %s，下次缴费日 %s。",
                        policy.policyNo(), policy.maskedHolder(), policy.product(), policy.status(),
                        policy.effectiveDate(), policy.expireDate(), policy.nextPaymentDate()), policy))
                .orElseGet(() -> ToolResult.fail("未找到该客户名下的保单，请核对保单号。"));
    }

    @Override public String adapterSource() { return adapter.source(); }
}
