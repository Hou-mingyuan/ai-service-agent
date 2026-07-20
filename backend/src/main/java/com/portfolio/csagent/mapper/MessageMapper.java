package com.portfolio.csagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.portfolio.csagent.entity.Message;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface MessageMapper extends BaseMapper<Message> {
    @Update("""
            UPDATE message SET read_at = #{now}
             WHERE tenant_id = #{tenantId} AND conversation_id = #{conversationId}
               AND id <= #{upToId} AND read_at IS NULL
               AND (sender_username IS NULL OR sender_username <> #{reader})
            """)
    int markRead(@Param("tenantId") String tenantId, @Param("conversationId") Long conversationId,
                 @Param("upToId") Long upToId, @Param("reader") String reader,
                 @Param("now") LocalDateTime now);
}
