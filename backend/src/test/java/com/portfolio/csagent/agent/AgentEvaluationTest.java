package com.portfolio.csagent.agent;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.csagent.adapter.business.BusinessSystemAdapter;
import com.portfolio.csagent.agent.tool.ToolExecutionContext;
import com.portfolio.csagent.agent.tool.ToolRegistry;
import com.portfolio.csagent.agent.tool.ToolResult;
import com.portfolio.csagent.entity.Conversation;
import com.portfolio.csagent.entity.Message;
import com.portfolio.csagent.entity.Ticket;
import com.portfolio.csagent.entity.ToolExecution;
import com.portfolio.csagent.llm.ChatMsg;
import com.portfolio.csagent.llm.LlmChatRequest;
import com.portfolio.csagent.llm.LlmChatResult;
import com.portfolio.csagent.llm.LlmClient;
import com.portfolio.csagent.llm.ToolCall;
import com.portfolio.csagent.mapper.MessageMapper;
import com.portfolio.csagent.mapper.TicketMapper;
import com.portfolio.csagent.mapper.ToolExecutionMapper;
import com.portfolio.csagent.security.AuthenticatedUser;
import com.portfolio.csagent.security.UserService;
import com.portfolio.csagent.service.ConversationService;
import com.portfolio.csagent.service.KnowledgeService;
import com.portfolio.csagent.service.TicketService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "app.llm.provider=mock",
        "app.business.adapter=mock",
        "app.sla.scan-interval-ms=3600000"
})
class AgentEvaluationTest {

    @Autowired private IntentService intentService;
    @Autowired private EmotionService emotionService;
    @Autowired private PromptSafetyService promptSafetyService;
    @Autowired private KnowledgeService knowledgeService;
    @Autowired private LlmClient llmClient;
    @Autowired private ToolRegistry toolRegistry;
    @Autowired private BusinessSystemAdapter businessAdapter;
    @Autowired private UserService userService;
    @Autowired private ConversationService conversationService;
    @Autowired private TicketService ticketService;
    @Autowired private MessageMapper messageMapper;
    @Autowired private TicketMapper ticketMapper;
    @Autowired private ToolExecutionMapper toolExecutionMapper;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("固定 Agent 评估集达到全部门槛并输出失败分类报告")
    void fixedEvaluationSetMeetsThresholds() throws Exception {
        List<EvalCase> cases = loadCases();
        EvaluationStats stats = new EvaluationStats();
        List<Failure> failures = new ArrayList<>();
        int passedCases = 0;

        for (EvalCase evalCase : cases) {
            int before = failures.size();
            evaluate(evalCase, stats, failures);
            if (failures.size() == before) {
                passedCases++;
            }
        }

        Map<String, Object> report = report(cases.size(), passedCases, stats, failures);
        Path reportPath = Path.of("target", "agent-evaluation-report.json");
        Files.createDirectories(reportPath.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);
        System.out.println("AGENT_EVAL " + objectMapper.writeValueAsString(report));

        assertTrue(cases.size() >= 30, "固定评估集不得少于 30 条");
        assertTrue(failures.isEmpty(), () -> "Agent 评估失败：" + failures);
    }

    private void evaluate(EvalCase evalCase, EvaluationStats stats, List<Failure> failures) throws Exception {
        if (evalCase.input() != null) {
            evaluateLanguageCase(evalCase, stats, failures);
        }
        if (evalCase.expectAuthorized() != null) {
            boolean actual = switch (evalCase.resourceType()) {
                case "order" -> businessAdapter.findOrder("demo", evalCase.owner(), evalCase.resourceId()).isPresent();
                case "policy" -> businessAdapter.findPolicy("demo", evalCase.owner(), evalCase.resourceId()).isPresent();
                default -> false;
            };
            check(evalCase, "authorization", evalCase.expectAuthorized() == actual,
                    "expected=" + evalCase.expectAuthorized() + ", actual=" + actual, stats, failures);
        }
        if (evalCase.scenario() != null) {
            evaluateScenario(evalCase, stats, failures);
        }
    }

    private void evaluateLanguageCase(EvalCase evalCase, EvaluationStats stats,
                                      List<Failure> failures) throws Exception {
        IntentResult intent = intentService.classify(evalCase.input());
        EmotionResult emotion = emotionService.detect(evalCase.input());
        PromptSafetyService.SafetyResult safety = promptSafetyService.inspect(evalCase.input());
        List<?> knowledge = knowledgeService.search("demo", evalCase.input(), 3, 0.2);

        if (evalCase.expectedIntent() != null) {
            check(evalCase, "intent", evalCase.expectedIntent().equals(intent.intent().name()),
                    "expected=" + evalCase.expectedIntent() + ", actual=" + intent.intent().name(), stats, failures);
        }
        if (evalCase.expectedSentiment() != null) {
            check(evalCase, "sentiment", evalCase.expectedSentiment().equals(emotion.sentiment().name()),
                    "expected=" + evalCase.expectedSentiment() + ", actual=" + emotion.sentiment().name(),
                    stats, failures);
        }
        if (evalCase.expectBlocked() != null) {
            check(evalCase, "prompt_safety", evalCase.expectBlocked() == !safety.allowed(),
                    "expectedBlocked=" + evalCase.expectBlocked() + ", actualBlocked=" + !safety.allowed(),
                    stats, failures);
        }
        if (evalCase.expectKnowledge() != null) {
            check(evalCase, "knowledge", evalCase.expectKnowledge() == !knowledge.isEmpty(),
                    "expectedKnowledge=" + evalCase.expectKnowledge() + ", matches=" + knowledge.size(),
                    stats, failures);
        }
        if (evalCase.expectHandoff() != null) {
            boolean faqNoAnswer = intent.intent() == Intent.FAQ && knowledge.isEmpty();
            boolean predicted = safety.allowed() && (intent.intent() == Intent.HUMAN_AGENT
                    || intent.intent() == Intent.TICKET_CREATE
                    || emotion.isNegative() && emotion.score() >= 0.6
                    || intent.confidence() < 0.4 || faqNoAnswer);
            check(evalCase, "handoff", evalCase.expectHandoff() == predicted,
                    "expected=" + evalCase.expectHandoff() + ", actual=" + predicted, stats, failures);
            if (evalCase.expectHandoff()) {
                stats.expectedHandoffs++;
                if (predicted) {
                    stats.recalledHandoffs++;
                }
            }
        }
        if (evalCase.expectedTool() != null) {
            LlmChatResult result = llmClient.chat(new LlmChatRequest(messages(evalCase), toolRegistry.specs()));
            boolean toolMatches = result.hasToolCalls()
                    && evalCase.expectedTool().equals(result.toolCalls().get(0).name());
            check(evalCase, "tool_selection", toolMatches,
                    "expected=" + evalCase.expectedTool() + ", actual="
                            + (result.hasToolCalls() ? result.toolCalls().get(0).name() : "none"),
                    stats, failures);
            if (toolMatches && evalCase.expectedArgs() != null) {
                ToolCall call = result.toolCalls().get(0);
                Map<String, Object> actual = objectMapper.readValue(call.arguments(), new TypeReference<>() { });
                for (Map.Entry<String, String> expected : evalCase.expectedArgs().entrySet()) {
                    String expectedValue = resolve(expected.getValue());
                    String actualValue = actual.get(expected.getKey()) == null
                            ? null : String.valueOf(actual.get(expected.getKey()));
                    check(evalCase, "parameter_extraction", expectedValue.equals(actualValue),
                            expected.getKey() + " expected=" + expectedValue + ", actual=" + actualValue,
                            stats, failures);
                }
            }
        }
    }

    private void evaluateScenario(EvalCase evalCase, EvaluationStats stats,
                                  List<Failure> failures) {
        boolean passed = switch (evalCase.scenario()) {
            case "message" -> duplicateMessage();
            case "ticket" -> duplicateTicket();
            case "tool" -> duplicateTool();
            case "mock" -> businessAdapter.mock() && "mock".equals(businessAdapter.source())
                    && businessAdapter.health().mock() && "UP".equals(businessAdapter.health().status());
            default -> false;
        };
        check(evalCase, evalCase.category(), passed, "scenario=" + evalCase.scenario(), stats, failures);
    }

    private boolean duplicateMessage() {
        AuthenticatedUser customer = userService.authenticate("customer", "customer123");
        Conversation conversation = conversationService.createFor(customer, "evaluation");
        String key = "eval-message-" + UUID.randomUUID();
        ConversationService.MessageResult first = conversationService.addCustomerMessage(
                conversation, customer, key, "重复消息评估");
        ConversationService.MessageResult second = conversationService.addCustomerMessage(
                conversation, customer, key, "重复消息评估");
        long count = messageMapper.selectCount(Wrappers.<Message>lambdaQuery()
                .eq(Message::getTenantId, customer.tenantId()).eq(Message::getClientMessageId, key));
        return !first.replayed() && second.replayed()
                && first.message().getId().equals(second.message().getId()) && count == 1;
    }

    private boolean duplicateTicket() {
        String key = "eval-ticket:" + UUID.randomUUID();
        TicketService.CreateCommand command = new TicketService.CreateCommand(
                "eval-" + UUID.randomUUID().toString().substring(0, 8), null,
                "OTHER", "重复建单评估", "幂等测试",
                "MEDIUM", "SYSTEM", "customer", key, "evaluation", "system");
        TicketService.CreateResult first = ticketService.createIdempotent(command);
        TicketService.CreateResult second = ticketService.createIdempotent(command);
        long count = ticketMapper.selectCount(Wrappers.<Ticket>lambdaQuery()
                .eq(Ticket::getTenantId, command.tenantId()).eq(Ticket::getIdempotencyKey, key));
        return !first.replayed() && second.replayed()
                && first.ticket().getId().equals(second.ticket().getId()) && count == 1;
    }

    private boolean duplicateTool() {
        AuthenticatedUser customer = userService.authenticate("customer", "customer123");
        Conversation conversation = conversationService.createFor(customer, "evaluation");
        String requestId = "eval-tool-" + UUID.randomUUID();
        ToolExecutionContext context = new ToolExecutionContext(customer, conversation, requestId, requestId, null);
        ToolResult first = toolRegistry.execute("query_order", Map.of("order_no", "123"), context);
        ToolResult second = toolRegistry.execute("query_order", Map.of("order_no", "123"), context);
        long count = toolExecutionMapper.selectCount(Wrappers.<ToolExecution>lambdaQuery()
                .eq(ToolExecution::getConversationId, conversation.getId())
                .eq(ToolExecution::getToolName, "query_order"));
        return !first.replayed() && second.replayed()
                && first.executionId().equals(second.executionId()) && count == 1;
    }

    private List<ChatMsg> messages(EvalCase evalCase) {
        List<ChatMsg> messages = new ArrayList<>();
        if (evalCase.context() != null) {
            for (String previous : evalCase.context()) {
                messages.add(ChatMsg.user(previous));
                messages.add(ChatMsg.assistant("已记录前序信息"));
            }
        }
        messages.add(ChatMsg.user(evalCase.input()));
        return messages;
    }

    private String resolve(String value) {
        if (value != null && value.startsWith("$TODAY+")) {
            return LocalDate.now().plusDays(Long.parseLong(value.substring(7))).toString();
        }
        return value;
    }

    private void check(EvalCase evalCase, String dimension, boolean passed, String detail,
                       EvaluationStats stats, List<Failure> failures) {
        stats.checks++;
        stats.dimensionTotals.merge(dimension, 1, Integer::sum);
        if (passed) {
            stats.passedChecks++;
            stats.dimensionPassed.merge(dimension, 1, Integer::sum);
        } else {
            failures.add(new Failure(evalCase.id(), evalCase.category(), dimension, detail));
        }
    }

    private Map<String, Object> report(int totalCases, int passedCases, EvaluationStats stats,
                                       List<Failure> failures) {
        Map<String, Object> dimensions = new LinkedHashMap<>();
        stats.dimensionTotals.forEach((name, total) -> dimensions.put(name, Map.of(
                "passed", stats.dimensionPassed.getOrDefault(name, 0),
                "total", total,
                "accuracy", ratio(stats.dimensionPassed.getOrDefault(name, 0), total))));
        Map<String, Long> failureCategories = failures.stream().collect(java.util.stream.Collectors.groupingBy(
                Failure::dimension, LinkedHashMap::new, java.util.stream.Collectors.counting()));
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("totalCases", totalCases);
        report.put("passedCases", passedCases);
        report.put("overallAccuracy", ratio(stats.passedChecks, stats.checks));
        report.put("handoffRecall", ratio(stats.recalledHandoffs, stats.expectedHandoffs));
        report.put("dimensions", dimensions);
        report.put("failureCategories", failureCategories);
        report.put("failures", failures);
        return report;
    }

    private double ratio(int numerator, int denominator) {
        return denominator == 0 ? 1.0 : Math.round(numerator * 10000.0 / denominator) / 10000.0;
    }

    private List<EvalCase> loadCases() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/agent-evaluation.json")) {
            if (input == null) {
                throw new IllegalStateException("agent-evaluation.json not found");
            }
            return objectMapper.readValue(input, new TypeReference<>() { });
        }
    }

    private record EvalCase(
            String id,
            String category,
            String input,
            List<String> context,
            String expectedIntent,
            String expectedTool,
            Map<String, String> expectedArgs,
            String expectedSentiment,
            Boolean expectHandoff,
            Boolean expectBlocked,
            Boolean expectKnowledge,
            String resourceType,
            String resourceId,
            String owner,
            Boolean expectAuthorized,
            String scenario) {
    }

    private record Failure(String id, String category, String dimension, String detail) {
    }

    private static final class EvaluationStats {
        private int checks;
        private int passedChecks;
        private int expectedHandoffs;
        private int recalledHandoffs;
        private final Map<String, Integer> dimensionTotals = new LinkedHashMap<>();
        private final Map<String, Integer> dimensionPassed = new LinkedHashMap<>();
    }
}
