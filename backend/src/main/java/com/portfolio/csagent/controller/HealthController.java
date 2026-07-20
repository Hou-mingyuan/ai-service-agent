package com.portfolio.csagent.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import com.portfolio.csagent.adapter.business.BusinessSystemAdapter;
import com.portfolio.csagent.common.ApiResponse;
import com.portfolio.csagent.config.AppProperties;
import com.portfolio.csagent.llm.LlmClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {
    private final LlmClient llmClient;
    private final BusinessSystemAdapter businessAdapter;
    private final JdbcTemplate jdbcTemplate;
    private final AppProperties properties;

    public HealthController(LlmClient llmClient, BusinessSystemAdapter businessAdapter,
                            JdbcTemplate jdbcTemplate, AppProperties properties) {
        this.llmClient = llmClient;
        this.businessAdapter = businessAdapter;
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("mode", properties.getDemo().isEnabled() ? "DEMO" : "STANDARD");
        result.put("llm", Map.of("provider", llmClient.provider(), "mock", "mock".equals(llmClient.provider())));
        BusinessSystemAdapter.AdapterHealth business = businessAdapter.health();
        result.put("business", Map.of("source", business.source(), "status", business.status(),
                "mock", business.mock(), "detail", business.detail()));
        result.put("knowledge", Map.of("source", "local-lexical", "mock", false));
        return ApiResponse.ok(result);
    }

    @GetMapping("/ready")
    public ApiResponse<Map<String, Object>> ready() {
        Integer database = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        BusinessSystemAdapter.AdapterHealth business = businessAdapter.health();
        boolean ready = database != null && database == 1 && "UP".equals(business.status());
        return ApiResponse.ok(Map.of("status", ready ? "READY" : "NOT_READY",
                "database", database != null && database == 1 ? "UP" : "DOWN",
                "businessAdapter", business.status()));
    }
}
