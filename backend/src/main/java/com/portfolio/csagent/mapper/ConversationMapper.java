package com.portfolio.csagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.portfolio.csagent.entity.Conversation;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ConversationMapper extends BaseMapper<Conversation> {
    @Update("""
            UPDATE conversation
               SET status = 'HUMAN_PENDING', bot_enabled = 0, assigned_agent = NULL,
                   claimed_at = NULL, handoff_reason = #{reason}, handoff_at = #{now},
                   updated_at = #{now}, version = version + 1
             WHERE id = #{id} AND tenant_id = #{tenantId} AND status = 'BOT' AND version = #{version}
            """)
    int requestHandoff(@Param("id") Long id, @Param("tenantId") String tenantId,
                       @Param("reason") String reason, @Param("now") LocalDateTime now,
                       @Param("version") Integer version);

    @Update("""
            UPDATE conversation
               SET status = 'HUMAN', bot_enabled = 0, assigned_agent = #{agent},
                   claimed_at = #{now}, updated_at = #{now}, version = version + 1
             WHERE id = #{id} AND tenant_id = #{tenantId}
               AND status = 'HUMAN_PENDING' AND assigned_agent IS NULL
            """)
    int claim(@Param("id") Long id, @Param("tenantId") String tenantId,
              @Param("agent") String agent, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE conversation
               SET status = 'BOT', bot_enabled = 1, assigned_agent = NULL, claimed_at = NULL,
                   updated_at = #{now}, version = version + 1
             WHERE id = #{id} AND tenant_id = #{tenantId} AND status = 'HUMAN' AND version = #{version}
            """)
    int resumeBot(@Param("id") Long id, @Param("tenantId") String tenantId,
                  @Param("now") LocalDateTime now, @Param("version") Integer version);
}
