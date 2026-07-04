package com.portfolio.csagent.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.portfolio.csagent.common.BizException;
import com.portfolio.csagent.entity.Ticket;
import com.portfolio.csagent.entity.TicketEvent;
import com.portfolio.csagent.mapper.TicketEventMapper;
import com.portfolio.csagent.mapper.TicketMapper;
import com.portfolio.csagent.ws.AgentEventSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private final TicketMapper ticketMapper;
    private final TicketEventMapper ticketEventMapper;
    private final AgentEventSocketHandler socket;

    public TicketService(TicketMapper ticketMapper, TicketEventMapper ticketEventMapper,
                         AgentEventSocketHandler socket) {
        this.ticketMapper = ticketMapper;
        this.ticketEventMapper = ticketEventMapper;
        this.socket = socket;
    }

    @Transactional
    public Ticket create(Long conversationId, String category, String title, String description,
                         String priority, String source, String customer) {
        Ticket t = new Ticket();
        t.setTicketNo(generateTicketNo());
        t.setConversationId(conversationId);
        t.setCategory(category == null ? "OTHER" : category);
        t.setTitle(title);
        t.setDescription(description);
        t.setPriority(priority == null ? "MEDIUM" : priority);
        t.setStatus(TicketStateMachine.OPEN);
        t.setSource(source == null ? "AGENT" : source);
        t.setCustomer(customer);
        LocalDateTime now = LocalDateTime.now();
        t.setCreatedAt(now);
        t.setUpdatedAt(now);
        ticketMapper.insert(t);
        recordEvent(t.getId(), null, TicketStateMachine.OPEN, "工单创建", source);
        log.info("创建工单 {}（类别={} 优先级={} 来源={}）", t.getTicketNo(), t.getCategory(),
                t.getPriority(), t.getSource());
        socket.broadcast("ticket.created", t);
        return t;
    }

    @Transactional
    public Ticket transition(Long ticketId, String toStatus, String operator, String note) {
        Ticket t = ticketMapper.selectById(ticketId);
        if (t == null) {
            throw new BizException(404, "工单不存在：" + ticketId);
        }
        if (!TicketStateMachine.isValidStatus(toStatus)) {
            throw new BizException("非法的工单状态：" + toStatus);
        }
        String from = t.getStatus();
        if (from.equals(toStatus)) {
            return t;
        }
        if (!TicketStateMachine.canTransition(from, toStatus)) {
            throw new BizException("工单状态不允许从 " + from + " 流转到 " + toStatus);
        }
        t.setStatus(toStatus);
        t.setUpdatedAt(LocalDateTime.now());
        if (TicketStateMachine.CLOSED.equals(toStatus)) {
            t.setClosedAt(LocalDateTime.now());
        }
        ticketMapper.updateById(t);
        recordEvent(ticketId, from, toStatus, note, operator);
        socket.broadcast("ticket.updated", t);
        return t;
    }

    @Transactional
    public Ticket assign(Long ticketId, String assignee) {
        Ticket t = ticketMapper.selectById(ticketId);
        if (t == null) {
            throw new BizException(404, "工单不存在：" + ticketId);
        }
        t.setAssignee(assignee);
        t.setUpdatedAt(LocalDateTime.now());
        ticketMapper.updateById(t);
        if (TicketStateMachine.canTransition(t.getStatus(), TicketStateMachine.IN_PROGRESS)) {
            return transition(ticketId, TicketStateMachine.IN_PROGRESS, assignee, "受理并指派给 " + assignee);
        }
        recordEvent(ticketId, t.getStatus(), t.getStatus(), "指派给 " + assignee, assignee);
        socket.broadcast("ticket.updated", t);
        return t;
    }

    private void recordEvent(Long ticketId, String from, String to, String note, String operator) {
        TicketEvent e = new TicketEvent();
        e.setTicketId(ticketId);
        e.setFromStatus(from);
        e.setToStatus(to);
        e.setNote(note);
        e.setOperator(operator == null ? "system" : operator);
        e.setCreatedAt(LocalDateTime.now());
        ticketEventMapper.insert(e);
    }

    private String generateTicketNo() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        Long count = ticketMapper.selectCount(
                Wrappers.<Ticket>lambdaQuery().ge(Ticket::getCreatedAt, start));
        long seq = (count == null ? 0L : count) + 1;
        return "TK" + today.format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
                + String.format("%04d", seq);
    }
}
