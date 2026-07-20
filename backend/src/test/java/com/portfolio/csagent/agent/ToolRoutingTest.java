package com.portfolio.csagent.agent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CancellationException;

import com.portfolio.csagent.agent.tool.ToolNames;
import com.portfolio.csagent.entity.Conversation;
import com.portfolio.csagent.entity.Message;
import com.portfolio.csagent.security.AuthenticatedUser;
import com.portfolio.csagent.security.UserService;
import com.portfolio.csagent.service.ConversationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 全链路（默认 Mock 模型）：意图 → 工具调用 → 流式答复 / 负面情绪转人工。
 */
@SpringBootTest
class ToolRoutingTest {

    @Autowired
    private AgentOrchestrator orchestrator;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private UserService userService;

    @Test
    @DisplayName("『帮我查订单123』应触发订单查询工具并给出含真实数据的答复")
    void orderQueryTriggersTool() {
        AuthenticatedUser customer = customer();
        Conversation c = conversationService.createFor(customer, "web");
        Message message = customerMessage(c, customer, "帮我查订单123");
        List<String> toolNames = new ArrayList<>();
        AgentReply reply = orchestrator.handle(c, "帮我查订单123",
                context(customer, message), collect(toolNames));

        assertTrue(toolNames.contains(ToolNames.QUERY_ORDER), "应调用 query_order 工具");
        assertTrue(reply.answer().contains("123"), "答复应包含订单号");
        assertTrue(reply.answer().contains("无线蓝牙耳机"), "答复应包含订单商品名（来自真实数据）");
        assertFalse(reply.handoff());
    }

    @Test
    @DisplayName("物流问题应路由到物流查询工具")
    void logisticsQueryTriggersTool() {
        AuthenticatedUser customer = customer();
        Conversation c = conversationService.createFor(customer, "web");
        Message message = customerMessage(c, customer, "订单123的快递到哪了");
        List<String> toolNames = new ArrayList<>();
        orchestrator.handle(c, "订单123的快递到哪了", context(customer, message), collect(toolNames));
        assertTrue(toolNames.contains(ToolNames.QUERY_LOGISTICS), "应调用 query_logistics 工具");
    }

    @Test
    @DisplayName("负面情绪/投诉应自动创建工单并转人工")
    void negativeEmotionEscalates() {
        AuthenticatedUser customer = customer();
        Conversation c = conversationService.createFor(customer, "web");
        Message message = customerMessage(c, customer, "你们太差了，我要投诉！");
        AgentReply reply = orchestrator.handle(c, "你们太差了，我要投诉！",
                context(customer, message), AgentEventSink.NOOP);
        assertTrue(reply.handoff(), "应触发转人工");
        assertNotNull(reply.ticket(), "应创建升级工单");
    }

    @Test
    @DisplayName("显式『转人工』应立即转接")
    void explicitHumanHandoff() {
        AuthenticatedUser customer = customer();
        Conversation c = conversationService.createFor(customer, "web");
        Message message = customerMessage(c, customer, "我要转人工");
        AgentReply reply = orchestrator.handle(c, "我要转人工", context(customer, message), AgentEventSink.NOOP);
        assertTrue(reply.handoff());
        assertNotNull(reply.ticket());
    }

    @Test
    @DisplayName("SSE 取消令牌应在工具调用前中止 Agent")
    void cancelledRequestStopsBeforeToolExecution() {
        AuthenticatedUser customer = customer();
        Conversation conversation = conversationService.createFor(customer, "web");
        Message message = customerMessage(conversation, customer, "帮我查订单123");
        List<String> toolNames = new ArrayList<>();
        String requestId = UUID.randomUUID().toString();
        AgentRequestContext cancelled = new AgentRequestContext(customer, requestId, requestId,
                message.getId(), new AtomicBoolean(true));

        assertThrows(CancellationException.class, () -> orchestrator.handle(
                conversation, "帮我查订单123", cancelled, collect(toolNames)));
        assertTrue(toolNames.isEmpty(), "取消后不得发起工具调用");
    }

    @SuppressWarnings("unchecked")
    private AgentEventSink collect(List<String> toolNames) {
        return (type, data) -> {
            if ("tool_call".equals(type)) {
                Object name = ((Map<String, Object>) data).get("name");
                toolNames.add(String.valueOf(name));
            }
        };
    }

    private AuthenticatedUser customer() {
        return userService.authenticate("customer", "customer123");
    }

    private Message customerMessage(Conversation conversation, AuthenticatedUser customer, String text) {
        return conversationService.addCustomerMessage(conversation, customer,
                UUID.randomUUID().toString(), text).message();
    }

    private AgentRequestContext context(AuthenticatedUser customer, Message message) {
        String requestId = UUID.randomUUID().toString();
        return new AgentRequestContext(customer, requestId, requestId, message.getId(), new AtomicBoolean(false));
    }
}
