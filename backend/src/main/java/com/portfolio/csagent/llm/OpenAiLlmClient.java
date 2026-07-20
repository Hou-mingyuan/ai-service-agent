package com.portfolio.csagent.llm;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.csagent.common.BizException;
import com.portfolio.csagent.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 面向任意「OpenAI 兼容」网关（OpenAI / DeepSeek / 通义 / 本地 vLLM 等）的客户端。
 * 通过 base-url + api-key + model 三项配置即可切换供应方。
 */
public class OpenAiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmClient.class);

    private final AppProperties.Llm cfg;
    private final ObjectMapper om;
    private final HttpClient http;

    public OpenAiLlmClient(AppProperties.Llm cfg, ObjectMapper om) {
        this.cfg = cfg;
        this.om = om;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public String provider() {
        return "openai";
    }

    @Override
    public LlmChatResult chat(LlmChatRequest request) {
        Map<String, Object> body = buildBody(request, false);
        HttpResponse<String> resp = send(body, false);
        if (resp.statusCode() >= 300) {
            throw new BizException(502, "LLM 网关暂时不可用（HTTP " + resp.statusCode() + "）");
        }
        try {
            JsonNode root = om.readTree(resp.body());
            JsonNode message = root.path("choices").path(0).path("message");
            String finish = root.path("choices").path(0).path("finish_reason").asText("stop");
            List<ToolCall> calls = new ArrayList<>();
            JsonNode toolCalls = message.path("tool_calls");
            if (toolCalls.isArray()) {
                for (JsonNode tc : toolCalls) {
                    calls.add(new ToolCall(
                            tc.path("id").asText(),
                            tc.path("function").path("name").asText(),
                            tc.path("function").path("arguments").asText("{}")));
                }
            }
            if (!calls.isEmpty()) {
                return LlmChatResult.tools(calls);
            }
            return new LlmChatResult(message.path("content").asText(""), List.of(), finish);
        } catch (Exception e) {
            throw new BizException(502, "LLM 网关返回了无法解析的响应");
        }
    }

    @Override
    public void chatStream(LlmChatRequest request, Consumer<String> onToken) {
        Map<String, Object> body = buildBody(request, true);
        String payload;
        try {
            payload = om.writeValueAsString(body);
        } catch (Exception e) {
            throw new BizException(500, "无法构造 LLM 请求");
        }
        HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(endpoint()))
                .timeout(Duration.ofSeconds(cfg.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + cfg.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<java.io.InputStream> resp =
                    http.send(httpReq, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() >= 300) {
                throw new BizException(502, "LLM 网关流式返回错误：" + resp.statusCode());
            }
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.isBlank() || !line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    JsonNode node = om.readTree(data);
                    String delta = node.path("choices").path(0).path("delta").path("content").asText("");
                    if (!delta.isEmpty()) {
                        onToken.accept(delta);
                    }
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(502, "LLM 流式请求失败");
        }
    }

    private HttpResponse<String> send(Map<String, Object> body, boolean stream) {
        try {
            String payload = om.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint()))
                    .timeout(Duration.ofSeconds(cfg.getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + cfg.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new BizException(502, "LLM 请求失败");
        }
    }

    private Map<String, Object> buildBody(LlmChatRequest request, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", cfg.getModel());
        body.put("temperature", cfg.getTemperature());
        body.put("stream", stream);
        List<Map<String, Object>> messages = new ArrayList<>();
        for (ChatMsg m : request.getMessages()) {
            messages.add(toOpenAiMessage(m));
        }
        body.put("messages", messages);
        if (!stream && request.isAllowTools() && request.getTools() != null && !request.getTools().isEmpty()) {
            List<Map<String, Object>> tools = new ArrayList<>();
            for (ToolSpec t : request.getTools()) {
                tools.add(Map.of("type", "function", "function", Map.of(
                        "name", t.name(),
                        "description", t.description(),
                        "parameters", t.parameters())));
            }
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }
        return body;
    }

    private Map<String, Object> toOpenAiMessage(ChatMsg m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("role", m.getRole());
        if (m.getToolCalls() != null && !m.getToolCalls().isEmpty()) {
            List<Map<String, Object>> tcs = new ArrayList<>();
            for (ToolCall tc : m.getToolCalls()) {
                tcs.add(Map.of("id", tc.id(), "type", "function",
                        "function", Map.of("name", tc.name(), "arguments", tc.arguments())));
            }
            map.put("tool_calls", tcs);
            map.put("content", m.getContent() == null ? "" : m.getContent());
        } else if ("tool".equals(m.getRole())) {
            map.put("tool_call_id", m.getToolCallId());
            map.put("name", m.getName());
            map.put("content", m.getContent());
        } else {
            map.put("content", m.getContent() == null ? "" : m.getContent());
        }
        return map;
    }

    private String endpoint() {
        String base = cfg.getBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/chat/completions";
    }

}
