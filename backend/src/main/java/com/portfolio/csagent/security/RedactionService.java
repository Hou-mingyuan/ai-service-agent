package com.portfolio.csagent.security;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

@Service
public class RedactionService {
    private static final int MAX_JSON_LENGTH = 8000;
    private final ObjectMapper objectMapper;

    public RedactionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String safeJson(Object value) {
        try {
            JsonNode tree = objectMapper.valueToTree(value == null ? Map.of() : value);
            redact(tree);
            String json = objectMapper.writeValueAsString(tree);
            return json.length() <= MAX_JSON_LENGTH ? json : json.substring(0, MAX_JSON_LENGTH) + "...";
        } catch (Exception ignored) {
            return "{\"redacted\":true}";
        }
    }

    public String maskAddress(String address) {
        if (address == null || address.isBlank()) {
            return address;
        }
        int keep = Math.min(6, address.length());
        return address.substring(0, keep) + "***";
    }

    public String maskName(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        return name.substring(0, 1) + "**";
    }

    private void redact(JsonNode node) {
        if (node instanceof ObjectNode object) {
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (isSensitiveKey(field.getKey())) {
                    object.put(field.getKey(), "[REDACTED]");
                } else {
                    redact(field.getValue());
                }
            }
        } else if (node instanceof ArrayNode array) {
            array.forEach(this::redact);
        }
    }

    private boolean isSensitiveKey(String key) {
        String lower = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return lower.contains("password") || lower.contains("secret") || lower.contains("apikey")
                || lower.contains("authorization") || lower.equals("token")
                || lower.contains("address") || lower.contains("holder");
    }
}
