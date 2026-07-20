package com.portfolio.csagent.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.portfolio.csagent.common.BizException;
import com.portfolio.csagent.entity.Conversation;
import com.portfolio.csagent.entity.Feedback;
import com.portfolio.csagent.entity.Message;
import com.portfolio.csagent.entity.Ticket;
import com.portfolio.csagent.llm.LlmClient;
import com.portfolio.csagent.mapper.ConversationMapper;
import com.portfolio.csagent.mapper.FeedbackMapper;
import com.portfolio.csagent.mapper.MessageMapper;
import com.portfolio.csagent.mapper.TicketMapper;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final TicketMapper ticketMapper;
    private final FeedbackMapper feedbackMapper;
    private final LlmClient llmClient;

    public DashboardService(ConversationMapper conversationMapper, MessageMapper messageMapper,
                            TicketMapper ticketMapper, FeedbackMapper feedbackMapper, LlmClient llmClient) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.ticketMapper = ticketMapper;
        this.feedbackMapper = feedbackMapper;
        this.llmClient = llmClient;
    }

    public Map<String, Object> overview(String tenantId, LocalDate from, LocalDate to) {
        validateRange(from, to);
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime endExclusive = to.plusDays(1).atStartOfDay();
        List<Conversation> conversations = conversationMapper.selectList(Wrappers.<Conversation>lambdaQuery()
                .eq(Conversation::getTenantId, tenantId)
                .ge(Conversation::getCreatedAt, start).lt(Conversation::getCreatedAt, endExclusive));
        List<Ticket> tickets = ticketMapper.selectList(Wrappers.<Ticket>lambdaQuery()
                .eq(Ticket::getTenantId, tenantId)
                .ge(Ticket::getCreatedAt, start).lt(Ticket::getCreatedAt, endExclusive));
        List<Feedback> feedback = feedbackMapper.selectList(Wrappers.<Feedback>lambdaQuery()
                .eq(Feedback::getTenantId, tenantId)
                .ge(Feedback::getCreatedAt, start).lt(Feedback::getCreatedAt, endExclusive));
        long messages = messageMapper.selectCount(Wrappers.<Message>lambdaQuery()
                .eq(Message::getTenantId, tenantId)
                .ge(Message::getCreatedAt, start).lt(Message::getCreatedAt, endExclusive));
        long toolCalls = messageMapper.selectCount(Wrappers.<Message>lambdaQuery()
                .eq(Message::getTenantId, tenantId).eq(Message::getRole, "tool")
                .ge(Message::getCreatedAt, start).lt(Message::getCreatedAt, endExclusive));

        long resolvedConversations = conversations.stream().filter(value -> value.getResolved() != null
                && value.getResolved() == 1).count();
        long handoffs = conversations.stream().filter(value -> value.getHandoffAt() != null).count();
        List<Long> responseSeconds = conversations.stream()
                .filter(value -> value.getFirstResponseAt() != null)
                .map(value -> Math.max(0, Duration.between(value.getCreatedAt(), value.getFirstResponseAt()).toSeconds()))
                .toList();
        double avgFirstResponse = responseSeconds.stream().mapToLong(Long::longValue).average().orElse(0);

        long openTickets = tickets.stream().filter(value -> List.of("OPEN", "IN_PROGRESS", "PENDING")
                .contains(value.getStatus())).count();
        long solvedTickets = tickets.stream().filter(value -> List.of("RESOLVED", "CLOSED")
                .contains(value.getStatus())).count();
        long breachedTickets = tickets.stream().filter(value -> value.getSlaBreachedAt() != null).count();
        double averageRating = feedback.stream().mapToInt(Feedback::getRating).average().orElse(0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("range", Map.of("from", from, "to", to, "timezone", "Asia/Shanghai"));
        result.put("kpis", Map.of(
                "conversations", conversations.size(),
                "avgFirstResponseSeconds", round(avgFirstResponse, 1),
                "resolutionRate", rate(resolvedConversations, conversations.size()),
                "handoffRate", rate(handoffs, conversations.size()),
                "openTickets", openTickets,
                "ticketResolutionRate", rate(solvedTickets, tickets.size()),
                "slaBreaches", breachedTickets,
                "satisfaction", round(averageRating, 2)));
        result.put("counts", Map.of("messages", messages, "toolCalls", toolCalls,
                "feedback", feedback.size(), "tickets", tickets.size()));
        result.put("tickets", Map.of(
                "byStatus", groupTickets(tickets, "status"),
                "byCategory", groupTickets(tickets, "category"),
                "byPriority", groupTickets(tickets, "priority")));
        result.put("trend", trend(from, to, conversations, tickets));
        result.put("definitions", Map.of(
                "avgFirstResponseSeconds", "会话创建至首条机器人或坐席回复的平均秒数，仅统计已有回复的会话",
                "resolutionRate", "范围内已关闭会话数 / 范围内新建会话数",
                "handoffRate", "范围内进入人工队列的会话数 / 范围内新建会话数",
                "ticketResolutionRate", "范围内已解决或已关闭工单数 / 范围内新建工单数",
                "slaBreaches", "范围内新建且记录 sla_breached_at 的工单数",
                "satisfaction", "范围内有效客户评分的算术平均值，空数据为 0"));
        result.put("llmProvider", llmClient.provider());
        return result;
    }

    private List<Map<String, Object>> trend(LocalDate from, LocalDate to,
                                            List<Conversation> conversations, List<Ticket> tickets) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            LocalDate current = date;
            long conversationCount = conversations.stream()
                    .filter(value -> value.getCreatedAt().toLocalDate().equals(current)).count();
            long handoffCount = conversations.stream().filter(value -> value.getHandoffAt() != null
                    && value.getHandoffAt().toLocalDate().equals(current)).count();
            long resolvedCount = tickets.stream().filter(value -> value.getClosedAt() != null
                    && value.getClosedAt().toLocalDate().equals(current)).count();
            long slaCount = tickets.stream().filter(value -> value.getSlaBreachedAt() != null
                    && value.getSlaBreachedAt().toLocalDate().equals(current)).count();
            rows.add(Map.of("date", current, "conversations", conversationCount, "handoffs", handoffCount,
                    "closedTickets", resolvedCount, "slaBreaches", slaCount));
        }
        return rows;
    }

    private Map<String, Long> groupTickets(List<Ticket> tickets, String field) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Ticket ticket : tickets) {
            String key = switch (field) {
                case "status" -> ticket.getStatus();
                case "category" -> ticket.getCategory();
                default -> ticket.getPriority();
            };
            result.merge(key == null ? "UNKNOWN" : key, 1L, Long::sum);
        }
        return result;
    }

    private double rate(long part, long total) {
        return total == 0 ? 0 : round(part * 100.0 / total, 1);
    }

    private double round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new BizException(400, "看板日期范围不正确");
        }
        if (Duration.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay()).toDays() > 90) {
            throw new BizException(400, "看板日期范围最多为90天");
        }
    }
}
