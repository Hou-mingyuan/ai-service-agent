package com.portfolio.csagent.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
                            TicketMapper ticketMapper, FeedbackMapper feedbackMapper,
                            LlmClient llmClient) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.ticketMapper = ticketMapper;
        this.feedbackMapper = feedbackMapper;
        this.llmClient = llmClient;
    }

    public Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<>();

        long convTotal = conversationMapper.selectCount(null);
        long convResolved = conversationMapper.selectCount(
                Wrappers.<Conversation>lambdaQuery().eq(Conversation::getResolved, 1));
        long convHuman = conversationMapper.selectCount(Wrappers.<Conversation>lambdaQuery()
                .in(Conversation::getStatus, "HUMAN_PENDING", "HUMAN"));
        Map<String, Object> conv = new LinkedHashMap<>();
        conv.put("total", convTotal);
        conv.put("resolved", convResolved);
        conv.put("human", convHuman);
        conv.put("resolveRate", rate(convResolved, convTotal));
        result.put("conversations", conv);

        long ticketTotal = ticketMapper.selectCount(null);
        long ticketOpen = ticketMapper.selectCount(Wrappers.<Ticket>lambdaQuery()
                .in(Ticket::getStatus, "OPEN", "IN_PROGRESS", "PENDING"));
        Map<String, Object> tickets = new LinkedHashMap<>();
        tickets.put("total", ticketTotal);
        tickets.put("open", ticketOpen);
        tickets.put("byStatus", groupCount("status"));
        tickets.put("byCategory", groupCount("category"));
        tickets.put("byPriority", groupCount("priority"));
        result.put("tickets", tickets);

        long msgTotal = messageMapper.selectCount(null);
        long toolCalls = messageMapper.selectCount(
                Wrappers.<Message>lambdaQuery().eq(Message::getRole, "tool"));
        Map<String, Object> messages = new LinkedHashMap<>();
        messages.put("total", msgTotal);
        messages.put("toolCalls", toolCalls);
        result.put("messages", messages);

        List<Map<String, Object>> agg = feedbackMapper.selectMaps(
                new QueryWrapper<Feedback>().select("avg(rating) as avg_rating", "count(*) as cnt"));
        Map<String, Object> sat = new LinkedHashMap<>();
        Object avg = agg.isEmpty() ? null : agg.get(0).get("avg_rating");
        Object cnt = agg.isEmpty() ? 0 : agg.get(0).get("cnt");
        sat.put("avgRating", avg == null ? 0 : new BigDecimal(avg.toString())
                .setScale(2, RoundingMode.HALF_UP));
        sat.put("count", cnt == null ? 0 : cnt);
        result.put("satisfaction", sat);

        result.put("llmProvider", llmClient.provider());
        return result;
    }

    private Map<String, Object> groupCount(String column) {
        QueryWrapper<Ticket> qw = new QueryWrapper<>();
        qw.select(column, "count(*) as cnt").groupBy(column);
        List<Map<String, Object>> rows = ticketMapper.selectMaps(qw);
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object key = row.get(column);
            Object cnt = row.get("cnt");
            out.put(key == null ? "UNKNOWN" : key.toString(), cnt);
        }
        return out;
    }

    private double rate(long part, long total) {
        if (total == 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(part * 100.0 / total).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
