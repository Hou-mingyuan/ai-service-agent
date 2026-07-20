package com.portfolio.csagent.adapter.business;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.csagent.common.BizException;
import com.portfolio.csagent.config.AppProperties;

public class HttpBusinessAdapter implements BusinessSystemAdapter {
    private final AppProperties.Business config;
    private final ObjectMapper objectMapper;
    private final HttpClient client;
    private final URI baseUri;

    public HttpBusinessAdapter(AppProperties.Business config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.baseUri = validateBase(config.getBaseUrl());
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, config.getTimeoutSeconds())))
                .build();
    }

    @Override
    public String source() {
        return "http";
    }

    @Override
    public boolean mock() {
        return false;
    }

    @Override
    public AdapterHealth health() {
        try {
            HttpResponse<String> response = send("GET", "/health", null, null, false);
            return new AdapterHealth(source(), response.statusCode() < 300 ? "UP" : "DOWN", false,
                    "HTTP " + response.statusCode());
        } catch (Exception exception) {
            return new AdapterHealth(source(), "DOWN", false, "连接失败");
        }
    }

    @Override
    public Optional<OrderData> findOrder(String tenantId, String ownerUsername, String orderNo) {
        return readOptional("/orders/" + encode(orderNo) + query(tenantId, ownerUsername), OrderData.class);
    }

    @Override
    public Optional<ShipmentData> findShipment(String tenantId, String ownerUsername,
                                               String orderNo, String trackingNo) {
        String identifier = orderNo != null ? "orderNo=" + encode(orderNo) : "trackingNo=" + encode(trackingNo);
        return readOptional("/shipments?" + identifier + "&tenantId=" + encode(tenantId)
                + "&ownerUsername=" + encode(ownerUsername), ShipmentData.class);
    }

    @Override
    public Optional<PolicyData> findPolicy(String tenantId, String ownerUsername, String policyNo) {
        return readOptional("/policies/" + encode(policyNo) + query(tenantId, ownerUsername), PolicyData.class);
    }

    @Override
    public RescheduleData reschedule(String tenantId, String ownerUsername, String orderNo,
                                     String policyNo, String newDate, String idempotencyKey) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "tenantId", tenantId,
                    "ownerUsername", ownerUsername,
                    "orderNo", orderNo == null ? "" : orderNo,
                    "policyNo", policyNo == null ? "" : policyNo,
                    "newDate", newDate));
            HttpResponse<String> response = send("POST", "/reschedules", body, idempotencyKey, true);
            ensureSuccess(response);
            return objectMapper.readValue(response.body(), RescheduleData.class);
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(502, "真实业务系统返回了无法解析的响应");
        }
    }

    private <T> Optional<T> readOptional(String path, Class<T> type) {
        try {
            HttpResponse<String> response = send("GET", path, null, null, true);
            if (response.statusCode() == 404) {
                return Optional.empty();
            }
            ensureSuccess(response);
            return Optional.of(objectMapper.readValue(response.body(), type));
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(502, "真实业务系统返回了无法解析的响应");
        }
    }

    private HttpResponse<String> send(String method, String path, String body,
                                      String idempotencyKey, boolean retryable) throws Exception {
        int attempts = retryable ? Math.max(1, config.getMaxRetries() + 1) : 1;
        Exception last = null;
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
                        .timeout(Duration.ofSeconds(Math.max(1, config.getTimeoutSeconds())))
                        .header("Accept", "application/json");
                if (config.getApiToken() != null && !config.getApiToken().isBlank()) {
                    builder.header("Authorization", "Bearer " + config.getApiToken());
                }
                if (idempotencyKey != null) {
                    builder.header("Idempotency-Key", idempotencyKey);
                }
                if ("POST".equals(method)) {
                    builder.header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
                } else {
                    builder.GET();
                }
                HttpResponse<String> response = client.send(builder.build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() < 500 || attempt == attempts - 1) {
                    return response;
                }
            } catch (Exception exception) {
                last = exception;
                if (attempt == attempts - 1) {
                    break;
                }
            }
        }
        throw new BizException(502, "真实业务系统连接失败" + (last == null ? "" : ""));
    }

    private void ensureSuccess(HttpResponse<String> response) {
        if (response.statusCode() >= 300) {
            throw new BizException(502, "真实业务系统暂时不可用（HTTP " + response.statusCode() + "）");
        }
    }

    private URI validateBase(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("BUSINESS_BASE_URL is required when BUSINESS_ADAPTER=http");
        }
        URI uri = URI.create(raw.endsWith("/") ? raw : raw + "/");
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null) {
            throw new IllegalStateException("BUSINESS_BASE_URL must be an absolute http(s) URL");
        }
        return uri;
    }

    private String query(String tenantId, String ownerUsername) {
        return "?tenantId=" + encode(tenantId) + "&ownerUsername=" + encode(ownerUsername);
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
