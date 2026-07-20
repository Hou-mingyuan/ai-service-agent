package com.portfolio.csagent.adapter.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.csagent.config.AppProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BusinessAdapterContractTest {
    private static final int PORT = 19049;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("真实 adapter 缺少地址时启动失败且不回退 Mock")
    void realAdapterRequiresExplicitBaseUrl() {
        AppProperties.Business config = new AppProperties.Business();
        config.setBaseUrl("");
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new HttpBusinessAdapter(config, objectMapper));
        assertTrue(failure.getMessage().contains("BUSINESS_BASE_URL"));
    }

    @Test
    @DisplayName("HTTP adapter 遵守查询、鉴权、重试和幂等契约")
    void httpAdapterUsesSharedContractAndForwardsIdempotency() throws Exception {
        AtomicInteger rescheduleAttempts = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
        server.createContext("/health", exchange -> json(exchange, 200, "{\"status\":\"UP\"}"));
        server.createContext("/orders/123", exchange -> {
            assertRequestContext(exchange);
            json(exchange, 200, "{\"orderNo\":\"123\",\"product\":\"真实商品\","
                    + "\"amount\":88.50,\"status\":\"PAID\",\"maskedAddress\":\"上海市***\","
                    + "\"dataSource\":\"http\"}");
        });
        server.createContext("/shipments", exchange -> {
            assertRequestContext(exchange);
            json(exchange, 200, "{\"orderNo\":\"123\",\"trackingNo\":\"REAL123\","
                    + "\"carrier\":\"真实物流\",\"status\":\"IN_TRANSIT\","
                    + "\"lastLocation\":\"上海分拨中心\",\"dataSource\":\"http\"}");
        });
        server.createContext("/policies/PAI2024001", exchange -> {
            assertRequestContext(exchange);
            json(exchange, 200, "{\"policyNo\":\"PAI2024001\",\"maskedHolder\":\"演**\","
                    + "\"product\":\"真实保单\",\"premium\":100.00,\"status\":\"ACTIVE\","
                    + "\"effectiveDate\":\"2026-01-01\",\"expireDate\":\"2026-12-31\","
                    + "\"nextPaymentDate\":\"2026-10-01\",\"dataSource\":\"http\"}");
        });
        server.createContext("/reschedules", exchange -> {
            assertRequestContext(exchange);
            assertEquals("adapter-idem-001", exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            JsonNode body = objectMapper.readTree(exchange.getRequestBody());
            assertEquals("demo", body.path("tenantId").asText());
            assertEquals("customer", body.path("ownerUsername").asText());
            assertEquals("123", body.path("orderNo").asText());
            if (rescheduleAttempts.incrementAndGet() == 1) {
                json(exchange, 503, "{\"message\":\"retry\"}");
            } else {
                json(exchange, 200, "{\"businessNo\":\"123\",\"newDate\":\"2026-08-01\","
                        + "\"status\":\"CONFIRMED\",\"dataSource\":\"http\"}");
            }
        });
        server.start();

        AppProperties.Business config = new AppProperties.Business();
        config.setBaseUrl("http://127.0.0.1:" + PORT);
        config.setApiToken("adapter-contract-token");
        config.setTimeoutSeconds(2);
        config.setMaxRetries(1);
        BusinessSystemAdapter adapter = new HttpBusinessAdapter(config, objectMapper);

        assertEquals("http", adapter.source());
        assertFalse(adapter.mock());
        assertEquals("UP", adapter.health().status());
        assertEquals("真实商品", adapter.findOrder("demo", "customer", "123").orElseThrow().product());
        assertEquals("REAL123", adapter.findShipment("demo", "customer", "123", null)
                .orElseThrow().trackingNo());
        assertEquals("真实保单", adapter.findPolicy("demo", "customer", "PAI2024001")
                .orElseThrow().product());
        assertEquals("CONFIRMED", adapter.reschedule("demo", "customer", "123", null,
                "2026-08-01", "adapter-idem-001").status());
        assertEquals(2, rescheduleAttempts.get());
    }

    private void assertRequestContext(HttpExchange exchange) {
        assertEquals("Bearer adapter-contract-token", exchange.getRequestHeaders().getFirst("Authorization"));
        if (!"/health".equals(exchange.getRequestURI().getPath())) {
            String query = exchange.getRequestURI().getRawQuery();
            if (!"/reschedules".equals(exchange.getRequestURI().getPath())) {
                assertTrue(query.contains("tenantId=demo"));
                assertTrue(query.contains("ownerUsername=customer"));
            }
        }
    }

    private void json(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
