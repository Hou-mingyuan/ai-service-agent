package com.portfolio.csagent.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.csagent.dto.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.security.rbac-enabled=true",
        "app.security.jwt-secret=test-rbac-jwt-secret-key-32chars-min!!"
})
class SecurityRbacTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    private String agentToken;
    private String supervisorToken;

    @BeforeEach
    void setUp() {
        agentToken = jwtService.createToken("agent", Role.AGENT);
        supervisorToken = jwtService.createToken("supervisor", Role.SUPERVISOR);
    }

    @Test
    @DisplayName("未认证访问 /api/tickets 返回 401")
    void ticketsWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("agent 角色可读取工单列表")
    void ticketsWithAgentTokenReturns200() throws Exception {
        mockMvc.perform(get("/api/tickets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("agent 无权访问 dashboard，返回 403")
    void dashboardWithAgentTokenReturns403() throws Exception {
        mockMvc.perform(get("/api/dashboard/overview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + agentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("supervisor 可访问 dashboard")
    void dashboardWithSupervisorTokenReturns200() throws Exception {
        mockMvc.perform(get("/api/dashboard/overview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("/api/auth/login 签发 JWT")
    void loginReturnsToken() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("agent");
        req.setPassword("agent123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.role").value("agent"));
    }
}
