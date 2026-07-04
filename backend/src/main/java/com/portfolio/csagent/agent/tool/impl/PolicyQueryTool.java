package com.portfolio.csagent.agent.tool.impl;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.portfolio.csagent.agent.tool.AgentTool;
import com.portfolio.csagent.agent.tool.ToolNames;
import com.portfolio.csagent.agent.tool.ToolResult;
import com.portfolio.csagent.agent.tool.ToolSupport;
import com.portfolio.csagent.entity.Policy;
import com.portfolio.csagent.llm.ToolSpec;
import com.portfolio.csagent.mapper.PolicyMapper;
import org.springframework.stereotype.Component;

@Component
public class PolicyQueryTool implements AgentTool {

    private final PolicyMapper policyMapper;

    public PolicyQueryTool(PolicyMapper policyMapper) {
        this.policyMapper = policyMapper;
    }

    @Override
    public String name() {
        return ToolNames.QUERY_POLICY;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(name(), "根据保单号查询保单详情（投保人、险种、状态、下次缴费日）",
                ToolSupport.schema(
                        Map.of("policy_no", ToolSupport.prop("string", "保单号，例如 PAI2024001")),
                        List.of("policy_no")));
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String policyNo = ToolSupport.str(args, "policy_no");
        if (policyNo == null) {
            return ToolResult.fail("请提供需要查询的保单号。");
        }
        Policy p = policyMapper.selectOne(
                Wrappers.<Policy>lambdaQuery().eq(Policy::getPolicyNo, policyNo).last("limit 1"));
        if (p == null) {
            return ToolResult.fail("未查询到保单号「" + policyNo + "」，请核对后重试。");
        }
        String summary = String.format(
                "保单 %s：投保人 %s，险种 %s，保费 ¥%s/年，状态：%s，保障期 %s 至 %s，下次缴费日：%s。",
                p.getPolicyNo(), p.getHolder(), p.getProduct(), p.getPremium(), statusCn(p.getStatus()),
                p.getEffectiveDate(), p.getExpireDate(), p.getNextPaymentDate());
        return ToolResult.ok(summary, p);
    }

    private String statusCn(String s) {
        if (s == null) {
            return "未知";
        }
        return switch (s) {
            case "ACTIVE" -> "生效中";
            case "LAPSED" -> "已失效";
            case "EXPIRED" -> "已到期";
            case "CANCELLED" -> "已退保";
            default -> s;
        };
    }
}
