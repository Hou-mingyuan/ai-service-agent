package com.portfolio.csagent.service;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.csagent.entity.RealtimeEvent;
import com.portfolio.csagent.mapper.RealtimeEventMapper;
import com.portfolio.csagent.security.AuthenticatedUser;
import com.portfolio.csagent.ws.AgentEventSocketHandler;
import com.portfolio.csagent.ws.RealtimeEnvelope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class RealtimeEventService {
    private final RealtimeEventMapper mapper;
    private final AgentEventSocketHandler socketHandler;
    private final ObjectMapper objectMapper;

    public RealtimeEventService(RealtimeEventMapper mapper, AgentEventSocketHandler socketHandler,
                                ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.socketHandler = socketHandler;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RealtimeEnvelope publishAgents(String tenantId, String type, Object payload) {
        return publish(tenantId, "AGENTS", null, type, payload);
    }

    @Transactional
    public RealtimeEnvelope publishUser(String tenantId, String username, String type, Object payload) {
        return publish(tenantId, "USER", username, type, payload);
    }

    @Transactional
    public RealtimeEnvelope publishTenant(String tenantId, String type, Object payload) {
        return publish(tenantId, "TENANT", null, type, payload);
    }

    /** One durable event visible to all staff and exactly one customer. */
    @Transactional
    public RealtimeEnvelope publishCase(String tenantId, String customerUsername, String type, Object payload) {
        return publish(tenantId, "CASE", customerUsername, type, payload);
    }

    public List<RealtimeEnvelope> replay(AuthenticatedUser user, long afterId, int limit) {
        var query = Wrappers.<RealtimeEvent>lambdaQuery()
                .eq(RealtimeEvent::getTenantId, user.tenantId())
                .gt(RealtimeEvent::getId, Math.max(0, afterId))
                .and(audience -> {
                    audience.eq(RealtimeEvent::getAudienceType, "TENANT")
                            .or(userEvent -> userEvent.eq(RealtimeEvent::getAudienceType, "USER")
                                    .eq(RealtimeEvent::getAudienceId, user.username()))
                            .or(caseEvent -> caseEvent.eq(RealtimeEvent::getAudienceType, "CASE")
                                    .eq(RealtimeEvent::getAudienceId, user.username()));
                    if (user.role().isStaff()) {
                        audience.or().eq(RealtimeEvent::getAudienceType, "AGENTS")
                                .or().eq(RealtimeEvent::getAudienceType, "CASE");
                    }
                })
                .orderByAsc(RealtimeEvent::getId)
                .last("limit " + Math.min(200, Math.max(1, limit)));
        return mapper.selectList(query)
                .stream()
                .map(this::toEnvelope)
                .toList();
    }

    private RealtimeEnvelope publish(String tenantId, String audienceType, String audienceId,
                                     String type, Object payload) {
        RealtimeEvent event = new RealtimeEvent();
        event.setTenantId(tenantId);
        event.setAudienceType(audienceType);
        event.setAudienceId(audienceId);
        event.setType(type);
        event.setPayloadJson(write(payload));
        event.setCreatedAt(LocalDateTime.now());
        mapper.insert(event);
        RealtimeEnvelope envelope = toEnvelope(event);
        broadcastAfterCommit(tenantId, audienceType, audienceId, envelope);
        return envelope;
    }

    private void broadcastAfterCommit(String tenantId, String audienceType, String audienceId,
                                      RealtimeEnvelope envelope) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            socketHandler.broadcast(tenantId, audienceType, audienceId, envelope);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                socketHandler.broadcast(tenantId, audienceType, audienceId, envelope);
            }
        });
    }

    private RealtimeEnvelope toEnvelope(RealtimeEvent event) {
        try {
            return new RealtimeEnvelope(event.getId(), event.getType(), event.getCreatedAt(),
                    objectMapper.readTree(event.getPayloadJson()));
        } catch (Exception exception) {
            return new RealtimeEnvelope(event.getId(), event.getType(), event.getCreatedAt(),
                    objectMapper.valueToTree(java.util.Map.of("error", "EVENT_PAYLOAD_UNAVAILABLE")));
        }
    }

    private String write(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Realtime payload cannot be serialized", exception);
        }
    }
}
