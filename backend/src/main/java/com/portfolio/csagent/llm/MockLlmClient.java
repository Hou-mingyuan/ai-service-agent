package com.portfolio.csagent.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.LocalDate;
import java.time.DayOfWeek;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.csagent.agent.tool.ToolNames;

/**
 * 离线内置模型：无需任何 API Key 即可跑通「意图 → 工具调用 → 流式答复」全链路，
 * 便于本地体验、单元测试与 CI。规则驱动，行为确定、可测。
 */
public class MockLlmClient implements LlmClient {

    private static final Pattern NUM = Pattern.compile("([A-Za-z]{0,4}\\d[A-Za-z0-9-]*)");
    private static final Pattern DATE = Pattern.compile(
            "(\\d{4}-\\d{1,2}-\\d{1,2}|\\d{1,2}[月/]\\d{1,2}[日号]?|明天|后天|下周[一二三四五六日天])");

    private final ObjectMapper om;

    public MockLlmClient(ObjectMapper om) {
        this.om = om;
    }

    @Override
    public String provider() {
        return "mock";
    }

    @Override
    public LlmChatResult chat(LlmChatRequest request) {
        // 已经有工具结果 => 处于收尾阶段，不再触发工具
        boolean hasToolResult = request.getMessages().stream()
                .anyMatch(m -> "tool".equals(m.getRole()));
        if (hasToolResult) {
            return LlmChatResult.text("");
        }
        String text = lastUser(request);
        String chosen = route(text, request.getTools());
        if (chosen == null) {
            return LlmChatResult.text("");
        }
        Map<String, Object> args = buildArgs(chosen, text, request);
        String argJson;
        try {
            argJson = om.writeValueAsString(args);
        } catch (Exception e) {
            argJson = "{}";
        }
        ToolCall call = new ToolCall("call_" + Math.abs(text.hashCode()), chosen, argJson);
        return LlmChatResult.tools(List.of(call));
    }

    @Override
    public void chatStream(LlmChatRequest request, Consumer<String> onToken) {
        streamText(compose(request), onToken);
    }

    // ---------------- 规则路由 ----------------

    private String route(String text, List<ToolSpec> tools) {
        if (text == null || text.isBlank()) {
            return null;
        }
        boolean logistics = containsAny(text, "物流", "快递", "发货", "到哪", "运单", "签收", "配送", "什么时候到");
        boolean order = containsAny(text, "订单", "order", "下单", "买的");
        boolean policy = containsAny(text, "保单", "保险", "投保", "理赔", "续保", "保费", "退保");
        boolean reschedule = containsAny(text, "改期", "改签", "重新预约", "换个时间", "预约变更", "改约", "延期");
        boolean ticketQuery = containsAny(text, "工单")
                && containsAny(text, "查", "进度", "状态", "查询", "处理", "怎么样了");
        boolean ticketCreate = containsAny(text, "创建工单", "提工单", "开工单", "登记", "帮我记录")
                || containsAny(text, "投诉", "差评", "曝光", "太差", "垃圾", "骗");

        if (reschedule && has(tools, ToolNames.RESCHEDULE)) {
            return ToolNames.RESCHEDULE;
        }
        if (ticketQuery && has(tools, ToolNames.QUERY_TICKET)) {
            return ToolNames.QUERY_TICKET;
        }
        if (ticketCreate && has(tools, ToolNames.CREATE_TICKET)) {
            return ToolNames.CREATE_TICKET;
        }
        if (logistics && has(tools, ToolNames.QUERY_LOGISTICS)) {
            return ToolNames.QUERY_LOGISTICS;
        }
        if (order && has(tools, ToolNames.QUERY_ORDER)) {
            return ToolNames.QUERY_ORDER;
        }
        if (policy && has(tools, ToolNames.QUERY_POLICY)) {
            return ToolNames.QUERY_POLICY;
        }
        return null;
    }

    private Map<String, Object> buildArgs(String tool, String text, LlmChatRequest request) {
        Map<String, Object> args = new LinkedHashMap<>();
        String code = firstMatch(NUM, text);
        if (code == null && requiresBusinessReference(tool)) {
            code = previousBusinessReference(request);
        }
        switch (tool) {
            case ToolNames.QUERY_ORDER -> args.put("order_no", code);
            case ToolNames.QUERY_LOGISTICS -> args.put(
                    containsAny(text, "运单", "tracking") ? "tracking_no" : "order_no", code);
            case ToolNames.QUERY_POLICY -> args.put("policy_no", code);
            case ToolNames.QUERY_TICKET -> args.put("ticket_no", code);
            case ToolNames.RESCHEDULE -> {
                args.put(isPolicyReference(text, request) ? "policy_no" : "order_no", code);
                args.put("new_date", resolveDate(firstMatch(DATE, text)));
            }
            case ToolNames.CREATE_TICKET -> {
                args.put("category", containsAny(text, "投诉", "差评", "曝光") ? "COMPLAINT" : "OTHER");
                args.put("title", text.length() > 30 ? text.substring(0, 30) : text);
                args.put("description", text);
            }
            default -> {
            }
        }
        return args;
    }

    private boolean requiresBusinessReference(String tool) {
        return ToolNames.QUERY_ORDER.equals(tool) || ToolNames.QUERY_LOGISTICS.equals(tool)
                || ToolNames.QUERY_POLICY.equals(tool) || ToolNames.RESCHEDULE.equals(tool);
    }

    private String previousBusinessReference(LlmChatRequest request) {
        boolean skippedCurrent = false;
        for (int i = request.getMessages().size() - 1; i >= 0; i--) {
            ChatMsg message = request.getMessages().get(i);
            if (!"user".equals(message.getRole())) {
                continue;
            }
            if (!skippedCurrent) {
                skippedCurrent = true;
                continue;
            }
            String match = firstMatch(NUM, message.getContent());
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private boolean isPolicyReference(String text, LlmChatRequest request) {
        if (containsAny(text, "保单", "保险", "policy")) {
            return true;
        }
        for (int i = request.getMessages().size() - 1; i >= 0; i--) {
            ChatMsg message = request.getMessages().get(i);
            if ("user".equals(message.getRole())
                    && containsAny(message.getContent(), "保单", "保险", "policy")) {
                return true;
            }
        }
        return false;
    }

    // ---------------- 收尾答复 ----------------

    private String compose(LlmChatRequest request) {
        List<String> toolSummaries = request.getMessages().stream()
                .filter(m -> "tool".equals(m.getRole()) && m.getContent() != null)
                .map(ChatMsg::getContent)
                .toList();
        if (!toolSummaries.isEmpty()) {
            return "已为您查询到以下信息：\n" + String.join("\n", toolSummaries)
                    + "\n\n还有什么可以帮您的吗？";
        }
        for (ChatMsg m : request.getMessages()) {
            if ("system".equals(m.getRole()) && m.getContent() != null
                    && m.getContent().startsWith("KNOWLEDGE_CONTEXT:")) {
                String context = m.getContent().substring("KNOWLEDGE_CONTEXT:".length()).trim();
                return context + "\n\n以上内容来自已标注来源的本地知识库；如仍未解决，可以申请人工坐席。";
            }
        }
        String user = lastUser(request);
        if (containsAny(user, "你好", "您好", "hi", "hello", "在吗", "在么")) {
            return "您好，我是智能客服「智答」，很高兴为您服务～"
                    + "您可以咨询订单、物流、保单、工单进度或预约改期等问题。";
        }
        return "我已经记录了您的问题：「" + user + "」。"
                + "如需查询订单/物流/保单，请提供对应单号；如需人工协助，请回复「转人工」。";
    }

    private void streamText(String text, Consumer<String> onToken) {
        int step = 4;
        for (int i = 0; i < text.length(); i += step) {
            onToken.accept(text.substring(i, Math.min(text.length(), i + step)));
        }
    }

    // ---------------- 工具方法 ----------------

    private String lastUser(LlmChatRequest request) {
        for (int i = request.getMessages().size() - 1; i >= 0; i--) {
            ChatMsg m = request.getMessages().get(i);
            if ("user".equals(m.getRole())) {
                return m.getContent() == null ? "" : m.getContent();
            }
        }
        return "";
    }

    private boolean has(List<ToolSpec> tools, String name) {
        return tools != null && tools.stream().anyMatch(t -> t.name().equals(name));
    }

    private boolean containsAny(String text, String... kws) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase();
        for (String kw : kws) {
            if (lower.contains(kw.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String firstMatch(Pattern p, String text) {
        if (text == null) {
            return null;
        }
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private String resolveDate(String raw) {
        if (raw == null) {
            return null;
        }
        if (raw.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) {
            String[] parts = raw.split("-");
            return LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])).toString();
        }
        if ("明天".equals(raw)) {
            return LocalDate.now().plusDays(1).toString();
        }
        if ("后天".equals(raw)) {
            return LocalDate.now().plusDays(2).toString();
        }
        if (raw.startsWith("下周")) {
            String days = "一二三四五六日天";
            int index = days.indexOf(raw.charAt(raw.length() - 1));
            int target = index < 0 ? 1 : Math.min(index + 1, 7);
            LocalDate date = LocalDate.now().plusWeeks(1).with(DayOfWeek.of(target));
            return date.toString();
        }
        return raw;
    }
}
