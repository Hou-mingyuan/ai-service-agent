package com.portfolio.csagent.ws;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.JsonNode;

public record RealtimeEnvelope(Long id, String type, LocalDateTime occurredAt, JsonNode payload) {
}
