package com.portfolio.csagent.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.portfolio.csagent.config.AppProperties;
import com.portfolio.csagent.entity.Ticket;
import com.portfolio.csagent.mapper.TicketMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class SlaService {
    private static final Logger log = LoggerFactory.getLogger(SlaService.class);

    private final TicketMapper ticketMapper;
    private final TicketService ticketService;
    private final AuditService auditService;
    private final AppProperties properties;
    private final Counter warningCounter;
    private final Counter breachCounter;

    public SlaService(TicketMapper ticketMapper, TicketService ticketService, AuditService auditService,
                      AppProperties properties, MeterRegistry meterRegistry) {
        this.ticketMapper = ticketMapper;
        this.ticketService = ticketService;
        this.auditService = auditService;
        this.properties = properties;
        this.warningCounter = meterRegistry.counter("csagent.sla.warnings");
        this.breachCounter = meterRegistry.counter("csagent.sla.breaches");
    }

    @Scheduled(fixedDelayString = "${app.sla.scan-interval-ms:30000}")
    public void scheduledScan() {
        try {
            process(LocalDateTime.now());
        } catch (Exception exception) {
            log.error("SLA scan failed", exception);
        }
    }

    public SlaRunResult process(LocalDateTime now) {
        int warnings = 0;
        int breaches = 0;
        LocalDateTime warningBoundary = now.plusMinutes(properties.getSla().getWarningMinutes());
        List<Ticket> warningCandidates = ticketMapper.selectList(Wrappers.<Ticket>lambdaQuery()
                .isNull(Ticket::getSlaWarningAt)
                .isNotNull(Ticket::getSlaDueAt)
                .le(Ticket::getSlaDueAt, warningBoundary)
                .notIn(Ticket::getStatus, TicketStateMachine.RESOLVED, TicketStateMachine.CLOSED)
                .orderByAsc(Ticket::getSlaDueAt)
                .last("limit 200"));
        for (Ticket candidate : warningCandidates) {
            if (ticketMapper.markSlaWarning(candidate.getId(), candidate.getTenantId(), now) == 1) {
                Ticket updated = ticketMapper.selectById(candidate.getId());
                String eventKey = "sla-warning:" + candidate.getSlaDueAt();
                ticketService.recordSystemEvent(updated, "SLA_WARNING", eventKey,
                        "工单即将达到 SLA 截止时间");
                auditService.recordSystem(updated.getTenantId(), "SLA_WARNING", "TICKET", updated.getId(),
                        "SUCCESS", eventKey, Map.of("dueAt", String.valueOf(updated.getSlaDueAt())));
                ticketService.publish(updated, "ticket.sla_warning");
                warningCounter.increment();
                warnings++;
            }
        }

        List<Ticket> breachCandidates = ticketMapper.selectList(Wrappers.<Ticket>lambdaQuery()
                .isNull(Ticket::getSlaBreachedAt)
                .isNotNull(Ticket::getSlaDueAt)
                .le(Ticket::getSlaDueAt, now)
                .notIn(Ticket::getStatus, TicketStateMachine.RESOLVED, TicketStateMachine.CLOSED)
                .orderByAsc(Ticket::getSlaDueAt)
                .last("limit 200"));
        for (Ticket candidate : breachCandidates) {
            if (ticketMapper.markSlaBreached(candidate.getId(), candidate.getTenantId(), now) == 1) {
                Ticket updated = ticketMapper.selectById(candidate.getId());
                String eventKey = "sla-breach:" + candidate.getSlaDueAt();
                ticketService.recordSystemEvent(updated, "SLA_BREACHED", eventKey,
                        "工单超过 SLA，已自动升级为紧急优先级");
                auditService.recordSystem(updated.getTenantId(), "SLA_BREACH", "TICKET", updated.getId(),
                        "SUCCESS", eventKey, Map.of("dueAt", String.valueOf(updated.getSlaDueAt())));
                ticketService.publish(updated, "ticket.sla_breached");
                breachCounter.increment();
                breaches++;
            }
        }
        return new SlaRunResult(warnings, breaches);
    }

    public record SlaRunResult(int warnings, int breaches) {
    }
}
