package com.portfolio.csagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.portfolio.csagent.entity.Ticket;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface TicketMapper extends BaseMapper<Ticket> {
    @Update("""
            UPDATE ticket
               SET assignee = #{assignee}, status = 'IN_PROGRESS', updated_at = #{now}, version = version + 1
             WHERE id = #{id} AND tenant_id = #{tenantId}
               AND assignee IS NULL AND status IN ('OPEN', 'PENDING')
            """)
    int claim(@Param("id") Long id, @Param("tenantId") String tenantId,
              @Param("assignee") String assignee, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE ticket SET sla_warning_at = #{now}, updated_at = #{now}, version = version + 1
             WHERE id = #{id} AND tenant_id = #{tenantId} AND sla_warning_at IS NULL
               AND status NOT IN ('RESOLVED', 'CLOSED')
            """)
    int markSlaWarning(@Param("id") Long id, @Param("tenantId") String tenantId,
                       @Param("now") LocalDateTime now);

    @Update("""
            UPDATE ticket
               SET sla_breached_at = #{now}, escalated_at = #{now}, priority = 'URGENT',
                   updated_at = #{now}, version = version + 1
             WHERE id = #{id} AND tenant_id = #{tenantId} AND sla_breached_at IS NULL
               AND status NOT IN ('RESOLVED', 'CLOSED')
            """)
    int markSlaBreached(@Param("id") Long id, @Param("tenantId") String tenantId,
                        @Param("now") LocalDateTime now);
}
