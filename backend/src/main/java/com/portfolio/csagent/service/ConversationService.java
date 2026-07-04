package com.portfolio.csagent.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.portfolio.csagent.common.BizException;
import com.portfolio.csagent.entity.Conversation;
import com.portfolio.csagent.entity.Message;
import com.portfolio.csagent.mapper.ConversationMapper;
import com.portfolio.csagent.mapper.MessageMapper;
import org.springframework.stereotype.Service;

@Service
public class ConversationService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;

    public ConversationService(ConversationMapper conversationMapper, MessageMapper messageMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
    }

    public Conversation create(String userName, String channel) {
        Conversation c = new Conversation();
        c.setSessionKey("S" + System.currentTimeMillis());
        c.setUserName(userName == null ? "访客" : userName);
        c.setChannel(channel == null ? "web" : channel);
        c.setStatus("BOT");
        c.setResolved(0);
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        conversationMapper.insert(c);
        return c;
    }

    public Conversation getOrCreate(Long conversationId, String userName) {
        if (conversationId != null) {
            Conversation c = conversationMapper.selectById(conversationId);
            if (c != null) {
                return c;
            }
        }
        return create(userName, "web");
    }

    public Conversation getById(Long id) {
        Conversation c = conversationMapper.selectById(id);
        if (c == null) {
            throw new BizException(404, "会话不存在：" + id);
        }
        return c;
    }

    public Message addMessage(Long conversationId, String role, String content) {
        return addMessage(conversationId, role, content, null, null, null, null, null, null);
    }

    public Message addMessage(Long conversationId, String role, String content, String intent,
                              String sentiment, Double sentimentScore, String toolName,
                              String toolArgs, String toolResult) {
        Message m = new Message();
        m.setConversationId(conversationId);
        m.setRole(role);
        m.setContent(content);
        m.setIntent(intent);
        m.setSentiment(sentiment);
        if (sentimentScore != null) {
            m.setSentimentScore(BigDecimal.valueOf(sentimentScore));
        }
        m.setToolName(toolName);
        m.setToolArgs(toolArgs);
        m.setToolResult(toolResult);
        m.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(m);
        return m;
    }

    public List<Message> history(Long conversationId, int limit) {
        List<Message> desc = messageMapper.selectList(Wrappers.<Message>lambdaQuery()
                .eq(Message::getConversationId, conversationId)
                .orderByDesc(Message::getId)
                .last("limit " + Math.max(1, limit)));
        java.util.Collections.reverse(desc);
        return desc;
    }

    public List<Message> messages(Long conversationId) {
        return messageMapper.selectList(Wrappers.<Message>lambdaQuery()
                .eq(Message::getConversationId, conversationId)
                .orderByAsc(Message::getId));
    }

    public void updateMeta(Conversation c, String intent, String sentiment) {
        c.setLastIntent(intent);
        c.setLastSentiment(sentiment);
        c.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(c);
    }

    public void updateStatus(Long conversationId, String status) {
        Conversation c = conversationMapper.selectById(conversationId);
        if (c != null) {
            c.setStatus(status);
            c.setUpdatedAt(LocalDateTime.now());
            if ("CLOSED".equals(status)) {
                c.setResolved(1);
            }
            conversationMapper.updateById(c);
        }
    }

    public List<Conversation> list(int limit) {
        return conversationMapper.selectList(Wrappers.<Conversation>lambdaQuery()
                .orderByDesc(Conversation::getId)
                .last("limit " + Math.max(1, limit)));
    }
}
