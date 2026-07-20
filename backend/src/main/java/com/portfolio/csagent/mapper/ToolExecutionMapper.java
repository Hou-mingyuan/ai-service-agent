package com.portfolio.csagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.portfolio.csagent.entity.ToolExecution;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ToolExecutionMapper extends BaseMapper<ToolExecution> {
    @Update("""
            UPDATE tool_execution
               SET status = 'RUNNING', confirmed_by = #{username}, confirmed_at = #{now}, updated_at = #{now}
             WHERE id = #{id} AND tenant_id = #{tenantId} AND customer_username = #{username}
               AND status = 'PENDING_CONFIRMATION'
            """)
    int confirm(@Param("id") Long id, @Param("tenantId") String tenantId,
                @Param("username") String username, @Param("now") LocalDateTime now);
}
