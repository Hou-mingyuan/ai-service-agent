package com.portfolio.csagent.service;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.portfolio.csagent.common.RequestIdContext;
import com.portfolio.csagent.entity.AuditLog;
import com.portfolio.csagent.mapper.AuditLogMapper;
import com.portfolio.csagent.security.AuthenticatedUser;
import com.portfolio.csagent.security.CurrentActor;
import com.portfolio.csagent.security.RedactionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final AuditLogMapper mapper;
    private final RedactionService redactionService;
    private final CurrentActor currentActor;

    public AuditService(AuditLogMapper mapper, RedactionService redactionService, CurrentActor currentActor) {
        this.mapper = mapper;
        this.redactionService = redactionService;
        this.currentActor = currentActor;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog record(String action, String resourceType, Object resourceId,
                           String outcome, String idempotencyKey, Object details) {
        return record(currentActor.require(), action, resourceType, resourceId, outcome, idempotencyKey, details);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog record(AuthenticatedUser actor, String action, String resourceType, Object resourceId,
                           String outcome, String idempotencyKey, Object details) {
        AuditLog log = new AuditLog();
        log.setTenantId(actor.tenantId());
        log.setRequestId(RequestIdContext.current());
        log.setActorUsername(actor.username());
        log.setActorRole(actor.role().getId());
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId == null ? null : String.valueOf(resourceId));
        log.setOutcome(outcome);
        log.setIdempotencyKey(idempotencyKey);
        log.setDetailsJson(redactionService.safeJson(details));
        log.setCreatedAt(LocalDateTime.now());
        mapper.insert(log);
        return log;
    }

    /** Records a successful business mutation in its existing transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditLog recordJoined(String tenantId, String actorUsername, String actorRole,
                                 String action, String resourceType, Object resourceId,
                                 String outcome, String idempotencyKey, Object details) {
        AuditLog log = new AuditLog();
        log.setTenantId(tenantId);
        log.setRequestId(RequestIdContext.current());
        log.setActorUsername(actorUsername);
        log.setActorRole(actorRole);
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId == null ? null : String.valueOf(resourceId));
        log.setOutcome(outcome);
        log.setIdempotencyKey(idempotencyKey);
        log.setDetailsJson(redactionService.safeJson(details));
        log.setCreatedAt(LocalDateTime.now());
        mapper.insert(log);
        return log;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAnonymous(String tenantId, String username, String action, String outcome, Object details) {
        AuditLog log = new AuditLog();
        log.setTenantId(tenantId);
        log.setRequestId(RequestIdContext.current());
        log.setActorUsername(username == null || username.isBlank() ? "anonymous" : username);
        log.setActorRole("anonymous");
        log.setAction(action);
        log.setResourceType("AUTH");
        log.setOutcome(outcome);
        log.setDetailsJson(redactionService.safeJson(details));
        log.setCreatedAt(LocalDateTime.now());
        mapper.insert(log);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSystem(String tenantId, String action, String resourceType, Object resourceId,
                             String outcome, String idempotencyKey, Object details) {
        AuditLog log = new AuditLog();
        log.setTenantId(tenantId);
        log.setRequestId(RequestIdContext.current());
        log.setActorUsername("system");
        log.setActorRole("system");
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId == null ? null : String.valueOf(resourceId));
        log.setOutcome(outcome);
        log.setIdempotencyKey(idempotencyKey);
        log.setDetailsJson(redactionService.safeJson(details));
        log.setCreatedAt(LocalDateTime.now());
        mapper.insert(log);
    }

    public IPage<AuditLog> page(String tenantId, int page, int size, String action, String actor) {
        return mapper.selectPage(Page.of(Math.max(1, page), Math.min(100, Math.max(1, size))),
                Wrappers.<AuditLog>lambdaQuery()
                        .eq(AuditLog::getTenantId, tenantId)
                        .eq(action != null && !action.isBlank(), AuditLog::getAction, action)
                        .eq(actor != null && !actor.isBlank(), AuditLog::getActorUsername, actor)
                        .orderByDesc(AuditLog::getId));
    }
}
