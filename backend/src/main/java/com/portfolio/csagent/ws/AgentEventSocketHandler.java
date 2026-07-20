package com.portfolio.csagent.ws;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class AgentEventSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(AgentEventSocketHandler.class);

    private final Map<WebSocketSession, Identity> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public AgentEventSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        Identity identity = new Identity(
                String.valueOf(session.getAttributes().get("tenantId")),
                String.valueOf(session.getAttributes().get("username")),
                String.valueOf(session.getAttributes().get("role")));
        sessions.put(session, identity);
        send(session, new RealtimeEnvelope(0L, "connection.ready", LocalDateTime.now(),
                objectMapper.valueToTree(Map.of("username", identity.username()))));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        JsonNode node;
        try {
            node = objectMapper.readTree(message.getPayload());
        } catch (Exception exception) {
            send(session, error("INVALID_MESSAGE", "消息格式不正确"));
            return;
        }
        if ("ping".equals(node.path("type").asText())) {
            send(session, new RealtimeEnvelope(0L, "pong", LocalDateTime.now(),
                    objectMapper.valueToTree(Map.of())));
        } else {
            send(session, error("READ_ONLY_CHANNEL", "业务消息请通过幂等 REST API 发送"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        sessions.remove(session);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    public void broadcast(String tenantId, String audienceType, String audienceId, RealtimeEnvelope envelope) {
        sessions.forEach((session, identity) -> {
            if (!session.isOpen() || !tenantId.equals(identity.tenantId())
                    || !canReceive(identity, audienceType, audienceId)) {
                return;
            }
            try {
                synchronized (session) {
                    send(session, envelope);
                }
            } catch (IOException exception) {
                sessions.remove(session);
                log.debug("Realtime send failed session={} type={}", session.getId(), envelope.type());
            }
        });
    }

    /** Compatibility path used only until callers are migrated to the persisted event service. */
    public void broadcast(String type, Object payload) {
        RealtimeEnvelope envelope = new RealtimeEnvelope(0L, type, LocalDateTime.now(),
                objectMapper.valueToTree(payload));
        sessions.forEach((session, identity) -> {
            if (!"customer".equals(identity.role())) {
                try {
                    send(session, envelope);
                } catch (IOException ignored) {
                    sessions.remove(session);
                }
            }
        });
    }

    public int onlineUsers() {
        return sessions.size();
    }

    private boolean canReceive(Identity identity, String audienceType, String audienceId) {
        return switch (audienceType) {
            case "USER" -> identity.username().equals(audienceId);
            case "AGENTS" -> !"customer".equals(identity.role());
            case "CASE" -> !"customer".equals(identity.role()) || identity.username().equals(audienceId);
            case "TENANT" -> true;
            default -> false;
        };
    }

    private void send(WebSocketSession session, RealtimeEnvelope envelope) throws IOException {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(envelope)));
    }

    private RealtimeEnvelope error(String code, String message) {
        return new RealtimeEnvelope(0L, "connection.error", LocalDateTime.now(),
                objectMapper.valueToTree(Map.of("code", code, "message", message)));
    }

    private record Identity(String tenantId, String username, String role) {
    }
}
