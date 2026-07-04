package com.portfolio.csagent.ws;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 坐席后台实时事件通道：把新建/流转的工单、转人工事件广播给所有在线坐席前端。
 */
@Component
public class AgentEventSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AgentEventSocketHandler.class);

    private final CopyOnWriteArraySet<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final ObjectMapper objectMapper;

    public AgentEventSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.debug("坐席 WebSocket 连接建立：{}（当前在线 {}）", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    /** 广播事件到所有在线坐席。type 例如 ticket.created / ticket.updated / conversation.handoff。 */
    public void broadcast(String type, Object payload) {
        String text;
        try {
            text = objectMapper.writeValueAsString(Map.of("type", type, "payload", payload));
        } catch (Exception e) {
            log.warn("事件序列化失败：{}", e.getMessage());
            return;
        }
        TextMessage msg = new TextMessage(text);
        for (WebSocketSession s : sessions) {
            try {
                if (s.isOpen()) {
                    synchronized (s) {
                        s.sendMessage(msg);
                    }
                }
            } catch (IOException e) {
                log.debug("向坐席 {} 推送失败：{}", s.getId(), e.getMessage());
            }
        }
    }

    public int onlineAgents() {
        return sessions.size();
    }
}
