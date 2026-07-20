package com.portfolio.csagent.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.portfolio.csagent.common.BizException;
import com.portfolio.csagent.config.AppProperties;
import com.portfolio.csagent.entity.Conversation;
import com.portfolio.csagent.entity.Ticket;
import com.portfolio.csagent.entity.TicketEvent;
import com.portfolio.csagent.mapper.ConversationMapper;
import com.portfolio.csagent.mapper.TicketEventMapper;
import com.portfolio.csagent.mapper.TicketMapper;
import com.portfolio.csagent.security.AuthenticatedUser;
import com.portfolio.csagent.security.CurrentActor;
import com.portfolio.csagent.security.Permission;
import com.portfolio.csagent.security.Role;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TicketService {
    private final TicketMapper ticketMapper;
    private final TicketEventMapper eventMapper;
    private final ConversationMapper conversationMapper;
    private final CurrentActor currentActor;
    private final AuditService auditService;
    private final RealtimeEventService realtimeEventService;
    private final AppProperties properties;
    private final TransactionTemplate transactionTemplate;

    public TicketService(TicketMapper ticketMapper, TicketEventMapper eventMapper,
                         ConversationMapper conversationMapper, CurrentActor currentActor,
                         AuditService auditService, RealtimeEventService realtimeEventService,
                         AppProperties properties, PlatformTransactionManager transactionManager) {
        this.ticketMapper = ticketMapper;
        this.eventMapper = eventMapper;
        this.conversationMapper = conversationMapper;
        this.currentActor = currentActor;
        this.auditService = auditService;
        this.realtimeEventService = realtimeEventService;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** Compatibility entry point for seed and legacy Agent callers. */
    public Ticket create(Long conversationId, String category, String title, String description,
                         String priority, String source, String customer) {
        Conversation conversation = conversationId == null ? null : conversationMapper.selectById(conversationId);
        String tenantId = conversation == null ? properties.getDemo().getTenantId() : conversation.getTenantId();
        String owner = conversation == null ? "customer" : conversation.getCustomerUsername();
        return createIdempotent(new CreateCommand(tenantId, conversationId, category, title, description,
                priority, source, owner, "ticket:" + UUID.randomUUID(), "system", "system")).ticket();
    }

    public CreateResult createHandoff(Conversation conversation, String category, String title,
                                      String description, String priority, String clientRequestId) {
        String handoffCycle = conversation.getHandoffAt() == null
                ? clientRequestId : conversation.getHandoffAt().toString();
        return createIdempotent(new CreateCommand(conversation.getTenantId(), conversation.getId(), category,
                title, description, priority, "AGENT", conversation.getCustomerUsername(),
                "handoff:" + conversation.getId() + ":" + handoffCycle, "system", "system"));
    }

    public CreateResult createManual(AuthenticatedUser actor, Long conversationId, String category,
                                     String title, String description, String priority, String idempotencyKey) {
        Conversation conversation = null;
        String customer = actor.username();
        if (conversationId != null) {
            conversation = conversationMapper.selectOne(Wrappers.<Conversation>lambdaQuery()
                    .eq(Conversation::getTenantId, actor.tenantId())
                    .eq(Conversation::getId, conversationId)
                    .last("limit 1"));
            if (conversation == null) {
                throw new BizException(404, "会话不存在");
            }
            customer = conversation.getCustomerUsername();
        }
        CreateResult result = createIdempotent(new CreateCommand(actor.tenantId(), conversationId, category,
                title, description, priority, "HUMAN", customer, idempotencyKey,
                actor.username(), actor.role().getId()));
        if (result.replayed()) {
            auditService.record(actor, "TICKET_CREATE", "TICKET", result.ticket().getId(),
                    "IDEMPOTENT_REPLAY", idempotencyKey,
                    Map.of("ticketNo", result.ticket().getTicketNo()));
        }
        return result;
    }

    public CreateResult createIdempotent(CreateCommand command) {
        validateCreate(command);
        Ticket created;
        try {
            created = transactionTemplate.execute(status -> {
                Ticket ticket = insertTicket(command);
                // Persist the shared customer/staff case event in the ticket transaction. Socket delivery is
                // deferred until commit by RealtimeEventService, so no rolled-back state leaks.
                publish(ticket, "ticket.created");
                auditService.recordJoined(command.tenantId(), command.operator(), command.operatorRole(),
                        "TICKET_CREATE", "TICKET", ticket.getId(), "SUCCESS", command.idempotencyKey(),
                        Map.of("ticketNo", ticket.getTicketNo()));
                return ticket;
            });
        } catch (DataIntegrityViolationException exception) {
            Ticket replay = findByIdempotency(command.tenantId(), command.idempotencyKey());
            if (replay == null) {
                throw exception;
            }
            return new CreateResult(replay, true);
        }
        if (created == null) {
            throw new BizException(500, "工单创建失败");
        }
        return new CreateResult(created, false);
    }

    public IPage<Ticket> page(String status, String category, int page, int size) {
        AuthenticatedUser actor = currentActor.require();
        if (!actor.role().getPermissions().contains(Permission.TICKET_READ)) {
            throw new BizException(403, "没有查看工单的权限");
        }
        var query = Wrappers.<Ticket>lambdaQuery()
                .eq(Ticket::getTenantId, actor.tenantId())
                .eq(status != null && !status.isBlank(), Ticket::getStatus, status)
                .eq(category != null && !category.isBlank(), Ticket::getCategory, category);
        if (actor.role() == Role.AGENT) {
            query.and(scope -> scope.isNull(Ticket::getAssignee).or().eq(Ticket::getAssignee, actor.username()));
        }
        query.orderByAsc(Ticket::getSlaDueAt).orderByDesc(Ticket::getId);
        return ticketMapper.selectPage(Page.of(Math.max(1, page), Math.min(100, Math.max(1, size))), query);
    }

    public TicketDetails detail(Long id) {
        AuthenticatedUser actor = currentActor.require();
        Ticket ticket = requireAccessible(id, actor);
        List<TicketEvent> events = eventMapper.selectList(Wrappers.<TicketEvent>lambdaQuery()
                .eq(TicketEvent::getTenantId, actor.tenantId())
                .eq(TicketEvent::getTicketId, id)
                .orderByAsc(TicketEvent::getId));
        return new TicketDetails(ticket, events);
    }

    @Transactional
    public Ticket claim(Long ticketId, String requestedAssignee) {
        AuthenticatedUser actor = currentActor.require();
        if (!actor.role().isStaff()) {
            throw new BizException(403, "没有认领工单的权限");
        }
        String assignee = actor.role().isSupervisorOrAbove() && requestedAssignee != null
                && !requestedAssignee.isBlank() ? requestedAssignee.trim() : actor.username();
        Ticket existing = ticketMapper.selectOne(Wrappers.<Ticket>lambdaQuery()
                .eq(Ticket::getTenantId, actor.tenantId()).eq(Ticket::getId, ticketId).last("limit 1"));
        if (existing == null) {
            throw new BizException(404, "工单不存在");
        }
        if (assignee.equals(existing.getAssignee())) {
            return existing;
        }
        int updated = ticketMapper.claim(ticketId, actor.tenantId(), assignee, LocalDateTime.now());
        if (updated != 1) {
            throw new BizException(409, "该工单已被其他坐席认领");
        }
        Ticket claimed = ticketMapper.selectById(ticketId);
        recordEvent(claimed, "ASSIGNED", "claim:" + claimed.getVersion(), existing.getStatus(),
                claimed.getStatus(), "认领并指派给 " + assignee, actor.username(), actor.role().getId(), null);
        auditService.record(actor, "TICKET_CLAIM", "TICKET", ticketId, "SUCCESS", null,
                Map.of("assignee", assignee));
        publish(claimed, "ticket.claimed");
        return claimed;
    }

    public Ticket claimLinkedConversation(Long conversationId) {
        AuthenticatedUser actor = currentActor.require();
        Ticket ticket = ticketMapper.selectOne(Wrappers.<Ticket>lambdaQuery()
                .eq(Ticket::getTenantId, actor.tenantId())
                .eq(Ticket::getConversationId, conversationId)
                .notIn(Ticket::getStatus, TicketStateMachine.RESOLVED, TicketStateMachine.CLOSED)
                .orderByDesc(Ticket::getId).last("limit 1"));
        return ticket == null ? null : claim(ticket.getId(), actor.username());
    }

    @Transactional
    public Ticket transition(Long ticketId, String toStatus, String note, String closeReason) {
        AuthenticatedUser actor = currentActor.require();
        Ticket ticket = requireAccessible(ticketId, actor);
        assertCanMutate(ticket, actor);
        if (!TicketStateMachine.isValidStatus(toStatus)) {
            throw new BizException(400, "工单状态不正确");
        }
        String from = ticket.getStatus();
        if (from.equals(toStatus)) {
            return ticket;
        }
        if (!TicketStateMachine.canTransition(from, toStatus)) {
            throw new BizException(409, "工单不能从 " + from + " 流转到 " + toStatus);
        }
        if (TicketStateMachine.RESOLVED.equals(toStatus) && (note == null || note.isBlank())) {
            throw new BizException(400, "解决工单必须填写处理结论");
        }
        if (TicketStateMachine.CLOSED.equals(toStatus) && (closeReason == null || closeReason.isBlank())) {
            throw new BizException(400, "关闭工单必须填写关闭原因");
        }
        applyTransition(ticket, toStatus, note, closeReason);
        if (ticketMapper.updateById(ticket) != 1) {
            throw new BizException(409, "工单已被其他操作更新，请刷新后重试");
        }
        recordEvent(ticket, "STATUS_CHANGED", "status:" + ticket.getVersion(), from, toStatus,
                note, actor.username(), actor.role().getId(), null);
        auditService.record(actor, "TICKET_TRANSITION", "TICKET", ticketId, "SUCCESS", null,
                Map.of("from", from, "to", toStatus, "closeReason", closeReason == null ? "" : closeReason));
        publish(ticket, "ticket.updated");
        return ticket;
    }

    @Transactional
    public Ticket reopen(Long ticketId, String reason) {
        AuthenticatedUser actor = currentActor.require();
        Ticket ticket = requireAccessible(ticketId, actor);
        assertCanMutate(ticket, actor);
        if (!(TicketStateMachine.RESOLVED.equals(ticket.getStatus())
                || TicketStateMachine.CLOSED.equals(ticket.getStatus()))) {
            throw new BizException(409, "只有已解决或已关闭工单可以重开");
        }
        String from = ticket.getStatus();
        ticket.setStatus(TicketStateMachine.IN_PROGRESS);
        ticket.setResolutionNote(null);
        ticket.setCloseReason(null);
        ticket.setClosedAt(null);
        ticket.setSlaDueAt(LocalDateTime.now().plusMinutes(slaMinutes(ticket.getPriority())));
        ticket.setSlaWarningAt(null);
        ticket.setSlaBreachedAt(null);
        ticket.setEscalatedAt(null);
        ticket.setUpdatedAt(LocalDateTime.now());
        if (ticketMapper.updateById(ticket) != 1) {
            throw new BizException(409, "工单已被其他操作更新，请刷新后重试");
        }
        recordEvent(ticket, "REOPENED", "reopen:" + ticket.getVersion(), from,
                TicketStateMachine.IN_PROGRESS, reason, actor.username(), actor.role().getId(), null);
        auditService.record(actor, "TICKET_REOPEN", "TICKET", ticketId, "SUCCESS", null,
                Map.of("reason", reason));
        publish(ticket, "ticket.reopened");
        return ticket;
    }

    /** Seed compatibility path; authenticated API uses transition(..., closeReason). */
    @Transactional
    public Ticket transitionSystem(Long ticketId, String toStatus, String operator, String note) {
        Ticket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new BizException(404, "工单不存在");
        }
        String from = ticket.getStatus();
        if (from.equals(toStatus)) {
            return ticket;
        }
        if (!TicketStateMachine.canTransition(from, toStatus)) {
            throw new BizException(409, "工单状态流转不合法");
        }
        applyTransition(ticket, toStatus, note,
                TicketStateMachine.CLOSED.equals(toStatus) ? (note == null ? "演示数据关闭" : note) : null);
        ticketMapper.updateById(ticket);
        recordEvent(ticket, "STATUS_CHANGED", "seed:" + ticket.getVersion(), from, toStatus,
                note, operator, "system", null);
        publish(ticket, "ticket.updated");
        return ticket;
    }

    /** Seed compatibility path. */
    @Transactional
    public Ticket assign(Long ticketId, String assignee) {
        Ticket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new BizException(404, "工单不存在");
        }
        String from = ticket.getStatus();
        ticket.setAssignee(assignee);
        ticket.setStatus(TicketStateMachine.IN_PROGRESS);
        ticket.setUpdatedAt(LocalDateTime.now());
        ticketMapper.updateById(ticket);
        recordEvent(ticket, "ASSIGNED", "seed-assign:" + ticket.getVersion(), from,
                ticket.getStatus(), "指派给 " + assignee, assignee, "system", null);
        publish(ticket, "ticket.updated");
        return ticket;
    }

    @Transactional
    public Ticket closeAfterFeedback(AuthenticatedUser customer, Long conversationId, String reason) {
        Ticket ticket = ticketMapper.selectOne(Wrappers.<Ticket>lambdaQuery()
                .eq(Ticket::getTenantId, customer.tenantId())
                .eq(Ticket::getConversationId, conversationId)
                .eq(Ticket::getCustomer, customer.username())
                .eq(Ticket::getStatus, TicketStateMachine.RESOLVED)
                .orderByDesc(Ticket::getId).last("limit 1"));
        if (ticket == null) {
            throw new BizException(409, "坐席尚未解决工单，暂不能结束服务");
        }
        String from = ticket.getStatus();
        applyTransition(ticket, TicketStateMachine.CLOSED, null, reason);
        if (ticketMapper.updateById(ticket) != 1) {
            throw new BizException(409, "工单状态已变化，请重试");
        }
        recordEvent(ticket, "CLOSED_BY_FEEDBACK", "feedback-close:" + conversationId,
                from, TicketStateMachine.CLOSED, reason, "customer", "customer", null);
        publish(ticket, "ticket.closed");
        return ticket;
    }

    public void recordSystemEvent(Ticket ticket, String eventType, String eventKey, String note) {
        try {
            recordEvent(ticket, eventType, eventKey, ticket.getStatus(), ticket.getStatus(), note,
                    "sla-scheduler", "system", null);
        } catch (DataIntegrityViolationException ignored) {
            // The unique event key is the second line of defense after the conditional ticket update.
        }
    }

    public void publish(Ticket ticket, String type) {
        if (ticket.getCustomer() != null) {
            realtimeEventService.publishCase(ticket.getTenantId(), ticket.getCustomer(), type, ticket);
        } else {
            realtimeEventService.publishAgents(ticket.getTenantId(), type, ticket);
        }
    }

    private Ticket insertTicket(CreateCommand command) {
        Ticket ticket = new Ticket();
        ticket.setTenantId(command.tenantId());
        ticket.setTicketNo(generateTicketNo());
        ticket.setIdempotencyKey(command.idempotencyKey());
        ticket.setConversationId(command.conversationId());
        ticket.setCategory(normalizeCategory(command.category()));
        ticket.setTitle(command.title().trim());
        ticket.setDescription(command.description());
        ticket.setPriority(normalizePriority(command.priority()));
        ticket.setStatus(TicketStateMachine.OPEN);
        ticket.setSource(command.source() == null ? "AGENT" : command.source());
        ticket.setCustomer(command.customerUsername());
        ticket.setVersion(0);
        LocalDateTime now = LocalDateTime.now();
        ticket.setSlaDueAt(now.plusMinutes(slaMinutes(ticket.getPriority())));
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);
        ticketMapper.insert(ticket);
        recordEvent(ticket, "CREATED", "created", null, TicketStateMachine.OPEN,
                "工单创建", command.operator(), command.operatorRole(), null);
        return ticket;
    }

    private void applyTransition(Ticket ticket, String toStatus, String note, String closeReason) {
        ticket.setStatus(toStatus);
        ticket.setUpdatedAt(LocalDateTime.now());
        if (TicketStateMachine.RESOLVED.equals(toStatus)) {
            ticket.setResolutionNote(note);
        }
        if (TicketStateMachine.CLOSED.equals(toStatus)) {
            ticket.setCloseReason(closeReason);
            ticket.setClosedAt(LocalDateTime.now());
        }
    }

    private void recordEvent(Ticket ticket, String eventType, String eventKey, String from, String to,
                             String note, String operator, String actorRole, String metadataJson) {
        TicketEvent event = new TicketEvent();
        event.setTenantId(ticket.getTenantId());
        event.setTicketId(ticket.getId());
        event.setEventType(eventType);
        event.setEventKey(eventKey);
        event.setFromStatus(from);
        event.setToStatus(to);
        event.setNote(note);
        event.setOperator(operator == null ? "system" : operator);
        event.setActorRole(actorRole == null ? "system" : actorRole);
        event.setMetadataJson(metadataJson);
        event.setCreatedAt(LocalDateTime.now());
        eventMapper.insert(event);
    }

    private Ticket requireAccessible(Long id, AuthenticatedUser actor) {
        Ticket ticket = ticketMapper.selectOne(Wrappers.<Ticket>lambdaQuery()
                .eq(Ticket::getTenantId, actor.tenantId()).eq(Ticket::getId, id).last("limit 1"));
        if (ticket == null || actor.role() == Role.AGENT
                && ticket.getAssignee() != null && !actor.username().equals(ticket.getAssignee())) {
            throw new BizException(404, "工单不存在");
        }
        return ticket;
    }

    private void assertCanMutate(Ticket ticket, AuthenticatedUser actor) {
        if (!actor.role().getPermissions().contains(Permission.TICKET_TRANSITION)) {
            throw new BizException(403, "没有流转工单的权限");
        }
        if (actor.role() == Role.AGENT && !actor.username().equals(ticket.getAssignee())) {
            throw new BizException(404, "工单不存在");
        }
    }

    private Ticket findByIdempotency(String tenantId, String key) {
        return ticketMapper.selectOne(Wrappers.<Ticket>lambdaQuery()
                .eq(Ticket::getTenantId, tenantId).eq(Ticket::getIdempotencyKey, key).last("limit 1"));
    }

    private void validateCreate(CreateCommand command) {
        if (command.idempotencyKey() == null || !command.idempotencyKey().matches("[A-Za-z0-9._:-]{8,96}")) {
            throw new BizException(400, "工单幂等键格式不正确");
        }
        if (command.title() == null || command.title().isBlank() || command.title().length() > 255) {
            throw new BizException(400, "工单标题不正确");
        }
    }

    private String generateTicketNo() {
        return "TK" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private String normalizePriority(String priority) {
        return priority == null || !priority.matches("LOW|MEDIUM|HIGH|URGENT") ? "MEDIUM" : priority;
    }

    private String normalizeCategory(String category) {
        return category == null || !category.matches("ORDER|LOGISTICS|POLICY|REFUND|COMPLAINT|OTHER")
                ? "OTHER" : category;
    }

    private int slaMinutes(String priority) {
        return switch (priority) {
            case "URGENT" -> properties.getSla().getUrgentMinutes();
            case "HIGH" -> properties.getSla().getHighMinutes();
            case "LOW" -> properties.getSla().getLowMinutes();
            default -> properties.getSla().getMediumMinutes();
        };
    }

    public record CreateCommand(String tenantId, Long conversationId, String category, String title,
                                String description, String priority, String source, String customerUsername,
                                String idempotencyKey, String operator, String operatorRole) {
    }

    public record CreateResult(Ticket ticket, boolean replayed) {
    }

    public record TicketDetails(Ticket ticket, List<TicketEvent> events) {
    }
}
