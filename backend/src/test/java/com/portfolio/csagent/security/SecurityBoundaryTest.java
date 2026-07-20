package com.portfolio.csagent.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.csagent.agent.tool.ToolExecutionContext;
import com.portfolio.csagent.agent.tool.ToolNames;
import com.portfolio.csagent.agent.tool.ToolRegistry;
import com.portfolio.csagent.agent.tool.ToolResult;
import com.portfolio.csagent.entity.AuditLog;
import com.portfolio.csagent.entity.Conversation;
import com.portfolio.csagent.entity.Ticket;
import com.portfolio.csagent.mapper.TicketMapper;
import com.portfolio.csagent.service.AuditService;
import com.portfolio.csagent.service.ConversationService;
import com.portfolio.csagent.service.TicketService;
import com.portfolio.csagent.service.TicketStateMachine;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.security.rbac-enabled=true",
        "app.security.jwt-secret=test-security-boundary-secret-32chars!!",
        "app.sla.scan-interval-ms=3600000"
})
class SecurityBoundaryTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtService jwtService;
    @Autowired private UserService userService;
    @Autowired private ConversationService conversationService;
    @Autowired private TicketService ticketService;
    @Autowired private TicketMapper ticketMapper;
    @Autowired private ToolRegistry toolRegistry;
    @Autowired private AuditService auditService;
    @Autowired private SecurityProperties securityProperties;

    @Test
    @DisplayName("健康与就绪探针无需登录即可供编排器调用")
    void healthAndReadinessProbesArePublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"));
        mockMvc.perform(get("/api/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.database").value("UP"));
    }

    @Test
    @DisplayName("匿名会话探测返回空身份，受保护身份接口仍返回 401")
    void anonymousSessionProbeDoesNotRelaxProtectedIdentityEndpoint() throws Exception {
        mockMvc.perform(get("/api/auth/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("客户枚举其他客户会话时返回 404 且带 request id")
    void customerCannotEnumerateAnotherCustomersConversation() throws Exception {
        AuthenticatedUser owner = userService.authenticate("customer", "customer123");
        Conversation conversation = conversationService.createFor(owner, "security-test");
        AuthenticatedUser intruder = new AuthenticatedUser(9001L, owner.tenantId(), "intruder",
                "其他客户", Role.CUSTOMER);

        mockMvc.perform(get("/api/conversations/{id}", conversation.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(intruder)))
                .andExpect(status().isNotFound())
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("会话不存在"));
    }

    @Test
    @DisplayName("普通坐席不能枚举已指派给其他坐席的工单")
    void agentCannotEnumerateAnotherAgentsTicket() throws Exception {
        AuthenticatedUser agent = userService.authenticate("agent", "agent123");
        Ticket ticket = ticketService.createIdempotent(new TicketService.CreateCommand(
                agent.tenantId(), null, "OTHER", "坐席隔离测试", "仅归属其他坐席",
                "MEDIUM", "SYSTEM", "customer", "security-ticket:" + UUID.randomUUID(),
                "test", "system")).ticket();
        Ticket assigned = ticketMapper.selectById(ticket.getId());
        assigned.setAssignee("another-agent");
        assigned.setStatus(TicketStateMachine.IN_PROGRESS);
        ticketMapper.updateById(assigned);

        mockMvc.perform(get("/api/tickets/{id}", ticket.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(agent)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("RBAC 区分客户、坐席和主管权限")
    void rolePermissionsAreEnforcedServerSide() throws Exception {
        AuthenticatedUser customer = userService.authenticate("customer", "customer123");
        AuthenticatedUser agent = userService.authenticate("agent", "agent123");
        AuthenticatedUser supervisor = userService.authenticate("supervisor", "super123");

        mockMvc.perform(get("/api/tickets").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(get("/api/knowledge/documents").header(HttpHeaders.AUTHORIZATION, bearer(agent)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(get("/api/audit").header(HttpHeaders.AUTHORIZATION, bearer(supervisor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("Cookie 写操作要求 CSRF，Bearer 写操作不依赖 CSRF")
    void cookieMutationsRequireCsrfButBearerMutationsDoNot() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"customer\",\"password\":\"customer123\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(securityProperties.getCookieName()))
                .andReturn();
        Cookie authCookie = login.getResponse().getCookie(securityProperties.getCookieName());

        mockMvc.perform(post("/api/auth/logout").cookie(authCookie))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/auth/logout").cookie(authCookie).with(csrf()))
                .andExpect(status().isOk());

        AuthenticatedUser customer = userService.authenticate("customer", "customer123");
        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("畸形 SSE 请求返回 HTTP 400 和 error 事件")
    void malformedChatRequestReturnsSseErrorEnvelope() throws Exception {
        AuthenticatedUser customer = userService.authenticate("customer", "customer123");

        mockMvc.perform(post("/api/chat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer))
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{broken"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:error")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"code\":400")));
    }

    @Test
    @DisplayName("防火墙拒绝的异常表单返回脱敏 400 而不是 500")
    void rejectedFormParameterReturnsSanitizedBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("{\"username\":\"customer\",\"password\":\"customer123\"}\n"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请求格式不正确"));
    }

    @Test
    @DisplayName("敏感执行单对其他客户隐藏，仅原客户可确认")
    void sensitiveConfirmationIsOwnerScoped() throws Exception {
        AuthenticatedUser customer = userService.authenticate("customer", "customer123");
        Conversation conversation = conversationService.createFor(customer, "security-test");
        String requestId = "security-reschedule-" + UUID.randomUUID();
        ToolResult pending = toolRegistry.execute(ToolNames.RESCHEDULE,
                Map.of("order_no", "123", "new_date", LocalDate.now().plusDays(8).toString()),
                new ToolExecutionContext(customer, conversation, requestId, requestId, null));
        AuthenticatedUser intruder = new AuthenticatedUser(9002L, customer.tenantId(), "intruder",
                "其他客户", Role.CUSTOMER);

        mockMvc.perform(post("/api/tool-executions/{id}/confirm", pending.executionId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(intruder)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404));
        mockMvc.perform(post("/api/tool-executions/{id}/confirm", pending.executionId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("审计详情递归脱敏密码、密钥、令牌和地址")
    void auditDetailsAreRedacted() {
        AuthenticatedUser customer = userService.authenticate("customer", "customer123");
        AuditLog log = auditService.record(customer, "SECURITY_REDACTION_TEST", "TEST", "1",
                "SUCCESS", "redact-" + UUID.randomUUID(), Map.of(
                        "password", "plain-password",
                        "nested", Map.of("apiKey", "secret-key", "token", "secret-token"),
                        "address", "完整家庭地址"));

        assertTrue(log.getDetailsJson().contains("[REDACTED]"));
        assertFalse(log.getDetailsJson().contains("plain-password"));
        assertFalse(log.getDetailsJson().contains("secret-key"));
        assertFalse(log.getDetailsJson().contains("secret-token"));
        assertFalse(log.getDetailsJson().contains("完整家庭地址"));
    }

    private String bearer(AuthenticatedUser user) {
        return "Bearer " + jwtService.createToken(user);
    }
}
