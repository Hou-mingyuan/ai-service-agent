package com.portfolio.csagent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.portfolio.csagent.agent.tool.ToolExecutionContext;
import com.portfolio.csagent.agent.tool.ToolNames;
import com.portfolio.csagent.agent.tool.ToolRegistry;
import com.portfolio.csagent.agent.tool.ToolResult;
import com.portfolio.csagent.common.BizException;
import com.portfolio.csagent.entity.Conversation;
import com.portfolio.csagent.entity.Ticket;
import com.portfolio.csagent.entity.TicketEvent;
import com.portfolio.csagent.entity.ToolExecution;
import com.portfolio.csagent.mapper.ConversationMapper;
import com.portfolio.csagent.mapper.TicketEventMapper;
import com.portfolio.csagent.mapper.TicketMapper;
import com.portfolio.csagent.mapper.ToolExecutionMapper;
import com.portfolio.csagent.security.AuthenticatedUser;
import com.portfolio.csagent.security.Role;
import com.portfolio.csagent.security.UserService;
import com.portfolio.csagent.ws.RealtimeEnvelope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "app.sla.scan-interval-ms=3600000")
class CoreWorkflowIntegrationTest {

    @Autowired private TicketService ticketService;
    @Autowired private ConversationService conversationService;
    @Autowired private HandoffService handoffService;
    @Autowired private SlaService slaService;
    @Autowired private RealtimeEventService realtimeEventService;
    @Autowired private ToolRegistry toolRegistry;
    @Autowired private UserService userService;
    @Autowired private TicketMapper ticketMapper;
    @Autowired private TicketEventMapper ticketEventMapper;
    @Autowired private ConversationMapper conversationMapper;
    @Autowired private ToolExecutionMapper toolExecutionMapper;

    @Test
    @DisplayName("并发重复建单只持久化一个工单")
    void concurrentTicketCreationIsIdempotent() throws Exception {
        String tenant = "ticket-concurrency-" + shortId();
        String key = "ticket:test:" + UUID.randomUUID();
        TicketService.CreateCommand command = new TicketService.CreateCommand(
                tenant, null, "OTHER", "并发幂等测试", "同一请求只能创建一次",
                "MEDIUM", "SYSTEM", "customer", key, "test", "system");

        List<TicketService.CreateResult> results = concurrent(8, () -> ticketService.createIdempotent(command));

        assertEquals(1, results.stream().map(result -> result.ticket().getId()).distinct().count());
        assertEquals(1, results.stream().filter(result -> !result.replayed()).count());
        assertEquals(1L, ticketMapper.selectCount(Wrappers.<Ticket>lambdaQuery()
                .eq(Ticket::getTenantId, tenant).eq(Ticket::getIdempotencyKey, key)));
        String expectedPrefix = "TK" + LocalDate.now(ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.BASIC_ISO_DATE) + "-";
        assertTrue(results.get(0).ticket().getTicketNo().startsWith(expectedPrefix));
    }

    @Test
    @DisplayName("双坐席并发认领时会话与工单只归属一个坐席")
    void concurrentHandoffClaimHasSingleWinner() throws Exception {
        AuthenticatedUser customer = userService.authenticate("customer", "customer123");
        Conversation conversation = conversationService.createFor(customer, "web");
        conversation = conversationService.requestHandoff(conversation, "并发认领测试");
        Long conversationId = conversation.getId();
        Ticket ticket = ticketService.createHandoff(conversation, "OTHER", "等待人工认领",
                "并发认领测试", "HIGH", "claim-" + shortId()).ticket();
        CountDownLatch start = new CountDownLatch(1);
        List<AuthenticatedUser> agents = List.of(
                actor(customer.tenantId(), "agent-a-" + shortId(), Role.AGENT),
                actor(customer.tenantId(), "agent-b-" + shortId(), Role.AGENT));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<ClaimAttempt>> futures = new ArrayList<>();
            for (AuthenticatedUser agent : agents) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        HandoffService.ClaimResult result = as(agent, () -> handoffService.claim(conversationId));
                        return new ClaimAttempt(true, agent.username(), result.ticket().getAssignee(), 0);
                    } catch (BizException exception) {
                        return new ClaimAttempt(false, agent.username(), null, exception.getCode());
                    }
                }));
            }
            start.countDown();
            List<ClaimAttempt> attempts = futures.stream().map(this::get).toList();
            assertEquals(1, attempts.stream().filter(ClaimAttempt::success).count());
            assertEquals(1, attempts.stream().filter(attempt -> attempt.code() == 409).count());
        } finally {
            pool.shutdownNow();
        }

        Conversation claimedConversation = conversationMapper.selectById(conversationId);
        Ticket claimedTicket = ticketMapper.selectById(ticket.getId());
        assertEquals(ConversationService.HUMAN, claimedConversation.getStatus());
        assertEquals(TicketStateMachine.IN_PROGRESS, claimedTicket.getStatus());
        assertEquals(claimedConversation.getAssignedAgent(), claimedTicket.getAssignee());
    }

    @Test
    @DisplayName("SLA 双实例扫描只产生一次预警和一次升级")
    void concurrentSlaScanIsIdempotent() throws Exception {
        String tenant = "sla-" + shortId();
        Ticket ticket = ticketService.createIdempotent(new TicketService.CreateCommand(
                tenant, null, "OTHER", "SLA 幂等测试", "模拟已过期工单", "HIGH",
                "SYSTEM", "customer", "sla:test:" + UUID.randomUUID(), "test", "system")).ticket();
        Ticket stale = ticketMapper.selectById(ticket.getId());
        LocalDateTime now = LocalDateTime.now();
        stale.setSlaDueAt(now.minusMinutes(1));
        stale.setSlaWarningAt(null);
        stale.setSlaBreachedAt(null);
        stale.setEscalatedAt(null);
        assertEquals(1, ticketMapper.updateById(stale));

        concurrent(2, () -> slaService.process(now));

        Ticket updated = ticketMapper.selectById(ticket.getId());
        assertNotNull(updated.getSlaWarningAt());
        assertNotNull(updated.getSlaBreachedAt());
        assertNotNull(updated.getEscalatedAt());
        assertEquals("URGENT", updated.getPriority());
        assertEquals(1L, eventCount(ticket.getId(), "SLA_WARNING"));
        assertEquals(1L, eventCount(ticket.getId(), "SLA_BREACHED"));
    }

    @Test
    @DisplayName("实时重放先按受众过滤再限制条数")
    void realtimeReplayCannotBeStarvedByOtherUsers() {
        String tenant = "replay-" + shortId();
        AuthenticatedUser customer = actor(tenant, "target", Role.CUSTOMER);
        AuthenticatedUser otherCustomer = actor(tenant, "other-customer", Role.CUSTOMER);
        AuthenticatedUser agent = actor(tenant, "case-agent", Role.AGENT);
        RealtimeEnvelope hidden = realtimeEventService.publishUser(tenant, "other", "message.hidden",
                Map.of("value", 1));
        RealtimeEnvelope visible = realtimeEventService.publishUser(tenant, customer.username(), "message.visible",
                Map.of("value", 2));
        RealtimeEnvelope caseEvent = realtimeEventService.publishCase(tenant, customer.username(), "ticket.case",
                Map.of("value", 3));

        List<RealtimeEnvelope> replay = realtimeEventService.replay(customer, hidden.id() - 1, 2);

        assertEquals(2, replay.size());
        assertEquals(visible.id(), replay.get(0).id());
        assertEquals("message.visible", replay.get(0).type());
        assertEquals(caseEvent.id(), replay.get(1).id());
        assertTrue(realtimeEventService.replay(otherCustomer, visible.id(), 10).isEmpty());
        assertEquals(caseEvent.id(), realtimeEventService.replay(agent, visible.id(), 10).get(0).id());
    }

    @Test
    @DisplayName("恢复机器人后再次转人工会创建新工单并可重新认领")
    void aNewHandoffCycleCreatesANewClaimableTicket() {
        AuthenticatedUser customer = userService.authenticate("customer", "customer123");
        AuthenticatedUser firstAgent = actor(customer.tenantId(), "cycle-a-" + shortId(), Role.AGENT);
        AuthenticatedUser secondAgent = actor(customer.tenantId(), "cycle-b-" + shortId(), Role.AGENT);
        Conversation conversation = conversationService.createFor(customer, "web");
        Conversation firstPending = conversationService.requestHandoff(conversation, "第一次转人工");
        Ticket firstTicket = ticketService.createHandoff(firstPending, "OTHER", "第一次转人工",
                "第一次服务周期", "MEDIUM", "cycle-first-" + shortId()).ticket();

        as(firstAgent, () -> handoffService.claim(conversation.getId()));
        as(firstAgent, () -> ticketService.transition(firstTicket.getId(), TicketStateMachine.RESOLVED,
                "第一次问题已解决", null));
        Conversation resumed = as(firstAgent, () -> conversationService.resumeBot(conversation.getId(),
                "恢复机器人继续服务"));
        assertEquals(ConversationService.BOT, resumed.getStatus());
        assertEquals(null, resumed.getAssignedAgent());

        Conversation secondPending = conversationService.requestHandoff(resumed, "第二次转人工");
        Ticket secondTicket = ticketService.createHandoff(secondPending, "OTHER", "第二次转人工",
                "第二次服务周期", "MEDIUM", "cycle-second-" + shortId()).ticket();
        HandoffService.ClaimResult secondClaim = as(secondAgent,
                () -> handoffService.claim(conversation.getId()));

        assertNotEquals(firstTicket.getId(), secondTicket.getId());
        assertEquals(secondTicket.getId(), secondClaim.ticket().getId());
        assertEquals(secondAgent.username(), secondClaim.ticket().getAssignee());
    }

    @Test
    @DisplayName("敏感改期确认前无副作用，确认后只执行一次且可重放")
    void sensitiveToolRequiresConfirmationAndReplays() {
        AuthenticatedUser customer = userService.authenticate("customer", "customer123");
        Conversation conversation = conversationService.createFor(customer, "web");
        String requestId = "reschedule-" + UUID.randomUUID();
        ToolExecutionContext context = new ToolExecutionContext(customer, conversation, requestId,
                requestId, null);
        Map<String, Object> args = Map.of("order_no", "123",
                "new_date", LocalDate.now().plusDays(10).toString());
        long ticketsBefore = ticketsForConversation(conversation.getId());

        ToolResult pending = toolRegistry.execute(ToolNames.RESCHEDULE, args, context);
        assertEquals("PENDING_CONFIRMATION", pending.status());
        assertEquals(ticketsBefore, ticketsForConversation(conversation.getId()));

        ToolResult completed = as(customer, () -> toolRegistry.confirm(pending.executionId()));
        ToolResult replayed = as(customer, () -> toolRegistry.confirm(pending.executionId()));
        ToolExecution execution = toolExecutionMapper.selectById(pending.executionId());

        assertEquals("COMPLETED", completed.status());
        assertFalse(completed.replayed());
        assertTrue(replayed.replayed());
        assertEquals("COMPLETED", execution.getStatus());
        assertNotNull(execution.getConfirmedAt());
        assertEquals(ticketsBefore + 1, ticketsForConversation(conversation.getId()));
    }

    private long eventCount(Long ticketId, String type) {
        return ticketEventMapper.selectCount(Wrappers.<TicketEvent>lambdaQuery()
                .eq(TicketEvent::getTicketId, ticketId).eq(TicketEvent::getEventType, type));
    }

    private long ticketsForConversation(Long conversationId) {
        return ticketMapper.selectCount(Wrappers.<Ticket>lambdaQuery()
                .eq(Ticket::getConversationId, conversationId));
    }

    private <T> List<T> concurrent(int workers, Callable<T> action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int index = 0; index < workers; index++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return action.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private AuthenticatedUser actor(String tenant, String username, Role role) {
        return new AuthenticatedUser(Math.abs((long) username.hashCode()), tenant, username, username, role);
    }

    private <T> T as(AuthenticatedUser actor, Callable<T> action) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                actor, null, actor.role().getAuthorities()));
        SecurityContextHolder.setContext(context);
        try {
            return action.call();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private <T> T get(Future<T> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record ClaimAttempt(boolean success, String actor, String assignee, int code) {
    }
}
