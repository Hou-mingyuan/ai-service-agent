package com.portfolio.csagent.agent.tool;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.csagent.common.BizException;
import com.portfolio.csagent.entity.Conversation;
import com.portfolio.csagent.entity.ToolExecution;
import com.portfolio.csagent.llm.ToolSpec;
import com.portfolio.csagent.mapper.ConversationMapper;
import com.portfolio.csagent.mapper.ToolExecutionMapper;
import com.portfolio.csagent.security.AuthenticatedUser;
import com.portfolio.csagent.security.CurrentActor;
import com.portfolio.csagent.service.AuditService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class ToolRegistry {
    private static final Duration CONFIRMATION_TTL = Duration.ofMinutes(10);

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();
    private final ToolArgumentValidator validator;
    private final ToolExecutionMapper executionMapper;
    private final ConversationMapper conversationMapper;
    private final CurrentActor currentActor;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final Executor toolExecutor;

    public ToolRegistry(List<AgentTool> toolList, ToolArgumentValidator validator,
                        ToolExecutionMapper executionMapper, ConversationMapper conversationMapper,
                        CurrentActor currentActor, AuditService auditService, ObjectMapper objectMapper,
                        @Qualifier("toolExecutor") Executor toolExecutor) {
        for (AgentTool tool : toolList) {
            if (tools.put(tool.name(), tool) != null) {
                throw new IllegalStateException("Duplicate Agent tool: " + tool.name());
            }
        }
        this.validator = validator;
        this.executionMapper = executionMapper;
        this.conversationMapper = conversationMapper;
        this.currentActor = currentActor;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.toolExecutor = toolExecutor;
    }

    public List<ToolSpec> specs() {
        return tools.values().stream().map(AgentTool::spec).toList();
    }

    public boolean has(String name) {
        return tools.containsKey(name);
    }

    public ToolResult execute(String name, Map<String, Object> rawArgs, ToolExecutionContext context) {
        AgentTool tool = requireTool(name);
        requirePermission(context.actor(), tool.requiredPermission());
        Map<String, Object> args = new LinkedHashMap<>(rawArgs == null ? Map.of() : rawArgs);
        validator.validate(tool.spec(), args);
        tool.validate(context, args);
        String executionKey = key(context, tool, args);
        ToolExecution existing = findByKey(context.actor().tenantId(), executionKey);
        if (existing != null) {
            return fromEntity(existing, true);
        }

        ToolExecution execution = new ToolExecution();
        execution.setTenantId(context.actor().tenantId());
        execution.setExecutionKey(executionKey);
        execution.setConversationId(context.conversation().getId());
        execution.setCustomerUsername(context.conversation().getCustomerUsername());
        execution.setToolName(tool.name());
        execution.setArgumentsJson(write(args));
        execution.setIsSensitive(tool.sensitive() ? 1 : 0);
        execution.setAdapterSource(tool.adapterSource());
        execution.setCreatedAt(LocalDateTime.now());
        execution.setUpdatedAt(LocalDateTime.now());

        if (tool.sensitive()) {
            execution.setStatus("PENDING_CONFIRMATION");
            execution.setSummary(tool.confirmationSummary(context, args));
            insertOrReplay(execution);
            ToolExecution persisted = findByKey(context.actor().tenantId(), executionKey);
            auditService.record(context.actor(), "TOOL_CONFIRMATION_REQUEST", "TOOL_EXECUTION",
                    persisted.getId(), "PENDING", executionKey,
                    Map.of("tool", tool.name(), "arguments", args, "adapter", tool.adapterSource()));
            return new ToolResult(true, persisted.getSummary(), args, "PENDING_CONFIRMATION",
                    persisted.getId(), tool.adapterSource(), persisted.getId() != execution.getId());
        }

        execution.setStatus("RUNNING");
        ToolExecution persisted = insertOrReplay(execution);
        if (!"RUNNING".equals(persisted.getStatus())) {
            return fromEntity(persisted, true);
        }
        ToolExecutionContext persistedContext = new ToolExecutionContext(context.actor(), context.conversation(),
                context.clientRequestId(), context.requestId(), executionKey);
        return perform(persisted, tool, persistedContext, args, false);
    }

    public ToolResult confirm(Long executionId) {
        AuthenticatedUser actor = currentActor.require();
        ToolExecution execution = executionMapper.selectOne(Wrappers.<ToolExecution>lambdaQuery()
                .eq(ToolExecution::getTenantId, actor.tenantId())
                .eq(ToolExecution::getId, executionId)
                .eq(ToolExecution::getCustomerUsername, actor.username())
                .last("limit 1"));
        if (execution == null) {
            throw new BizException(404, "待确认操作不存在");
        }
        if ("COMPLETED".equals(execution.getStatus())) {
            return fromEntity(execution, true);
        }
        if ("RUNNING".equals(execution.getStatus())) {
            throw new BizException(409, "该操作正在执行，请稍后刷新");
        }
        if (!"PENDING_CONFIRMATION".equals(execution.getStatus())) {
            throw new BizException(409, "该操作当前不能确认");
        }
        if (execution.getCreatedAt().plus(CONFIRMATION_TTL).isBefore(LocalDateTime.now())) {
            execution.setStatus("EXPIRED");
            execution.setUpdatedAt(LocalDateTime.now());
            executionMapper.updateById(execution);
            auditService.record(actor, "TOOL_CONFIRM", "TOOL_EXECUTION", executionId,
                    "EXPIRED", execution.getExecutionKey(), Map.of("tool", execution.getToolName()));
            throw new BizException(409, "确认已过期，请重新发起操作");
        }
        AgentTool tool = requireTool(execution.getToolName());
        requirePermission(actor, tool.requiredPermission());
        if (executionMapper.confirm(executionId, actor.tenantId(), actor.username(), LocalDateTime.now()) != 1) {
            throw new BizException(409, "该操作已被处理，请刷新后重试");
        }
        execution = executionMapper.selectById(executionId);
        Conversation conversation = conversationMapper.selectOne(Wrappers.<Conversation>lambdaQuery()
                .eq(Conversation::getTenantId, actor.tenantId())
                .eq(Conversation::getId, execution.getConversationId())
                .eq(Conversation::getCustomerUsername, actor.username())
                .last("limit 1"));
        if (conversation == null) {
            throw new BizException(404, "会话不存在");
        }
        Map<String, Object> args = readMap(execution.getArgumentsJson());
        tool.validate(new ToolExecutionContext(actor, conversation, execution.getExecutionKey(),
                "confirmation", execution.getExecutionKey()), args);
        ToolExecutionContext context = new ToolExecutionContext(actor, conversation,
                execution.getExecutionKey(), "confirmation", execution.getExecutionKey());
        return perform(execution, tool, context, args, true);
    }

    public List<ToolExecution> list(Long conversationId) {
        AuthenticatedUser actor = currentActor.require();
        Conversation conversation = conversationMapper.selectOne(Wrappers.<Conversation>lambdaQuery()
                .eq(Conversation::getTenantId, actor.tenantId())
                .eq(Conversation::getId, conversationId)
                .last("limit 1"));
        if (conversation == null || !actor.role().isStaff()
                && !actor.username().equals(conversation.getCustomerUsername())) {
            throw new BizException(404, "会话不存在");
        }
        return executionMapper.selectList(Wrappers.<ToolExecution>lambdaQuery()
                .eq(ToolExecution::getTenantId, actor.tenantId())
                .eq(ToolExecution::getConversationId, conversationId)
                .orderByAsc(ToolExecution::getId));
    }

    private ToolResult perform(ToolExecution execution, AgentTool tool, ToolExecutionContext context,
                               Map<String, Object> args, boolean confirmed) {
        long started = System.nanoTime();
        CompletableFuture<ToolResult> future = CompletableFuture.supplyAsync(
                () -> tool.execute(context, args), toolExecutor);
        try {
            ToolResult raw = future.get(Math.max(1, tool.timeoutSeconds()), TimeUnit.SECONDS);
            execution.setStatus("COMPLETED");
            execution.setSummary(raw.summary());
            execution.setResultJson(write(raw.data()));
            execution.setErrorCode(raw.ok() ? null : "NO_RESULT");
            execution.setDurationMs(elapsedMs(started));
            execution.setUpdatedAt(LocalDateTime.now());
            executionMapper.updateById(execution);
            auditService.record(context.actor(), confirmed ? "TOOL_CONFIRM_EXECUTE" : "TOOL_EXECUTE",
                    "TOOL_EXECUTION", execution.getId(), raw.ok() ? "SUCCESS" : "NO_RESULT",
                    execution.getExecutionKey(), Map.of("tool", tool.name(), "arguments", args,
                            "adapter", tool.adapterSource(), "durationMs", execution.getDurationMs()));
            return new ToolResult(raw.ok(), raw.summary(), raw.data(), "COMPLETED", execution.getId(),
                    tool.adapterSource(), false);
        } catch (TimeoutException exception) {
            future.cancel(true);
            return failExecution(execution, tool, context, "TIMEOUT", "工具处理超时，操作未被确认为成功", started);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return failExecution(execution, tool, context, "CANCELLED", "工具调用已取消", started);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            String message = cause instanceof BizException biz ? biz.getMessage() : "工具执行失败，请稍后重试";
            return failExecution(execution, tool, context, "EXECUTION_FAILED", message, started);
        }
    }

    private ToolResult failExecution(ToolExecution execution, AgentTool tool, ToolExecutionContext context,
                                     String code, String message, long started) {
        execution.setStatus("FAILED");
        execution.setSummary(message);
        execution.setErrorCode(code);
        execution.setDurationMs(elapsedMs(started));
        execution.setUpdatedAt(LocalDateTime.now());
        executionMapper.updateById(execution);
        auditService.record(context.actor(), "TOOL_EXECUTE", "TOOL_EXECUTION", execution.getId(),
                "FAILED", execution.getExecutionKey(), Map.of("tool", tool.name(), "errorCode", code,
                        "durationMs", execution.getDurationMs()));
        return new ToolResult(false, message, null, "FAILED", execution.getId(), tool.adapterSource(), false);
    }

    private ToolExecution insertOrReplay(ToolExecution execution) {
        try {
            executionMapper.insert(execution);
            return execution;
        } catch (DataIntegrityViolationException exception) {
            ToolExecution replay = findByKey(execution.getTenantId(), execution.getExecutionKey());
            if (replay == null) {
                throw exception;
            }
            return replay;
        }
    }

    private ToolResult fromEntity(ToolExecution execution, boolean replayed) {
        Object data = readNode(execution.getResultJson());
        boolean ok = "COMPLETED".equals(execution.getStatus()) && execution.getErrorCode() == null
                || "PENDING_CONFIRMATION".equals(execution.getStatus());
        if ("PENDING_CONFIRMATION".equals(execution.getStatus())) {
            data = readNode(execution.getArgumentsJson());
        }
        return new ToolResult(ok, execution.getSummary(), data, execution.getStatus(), execution.getId(),
                execution.getAdapterSource(), replayed);
    }

    private AgentTool requireTool(String name) {
        AgentTool tool = tools.get(name);
        if (tool == null) {
            throw new BizException(400, "模型请求了未注册工具");
        }
        return tool;
    }

    private void requirePermission(AuthenticatedUser actor, String permission) {
        if (!actor.role().getPermissions().contains(permission)) {
            throw new BizException(403, "没有调用该业务工具的权限");
        }
    }

    private ToolExecution findByKey(String tenantId, String executionKey) {
        return executionMapper.selectOne(Wrappers.<ToolExecution>lambdaQuery()
                .eq(ToolExecution::getTenantId, tenantId)
                .eq(ToolExecution::getExecutionKey, executionKey)
                .last("limit 1"));
    }

    private String key(ToolExecutionContext context, AgentTool tool, Map<String, Object> args) {
        return "tool:" + sha256(context.actor().tenantId() + "|" + context.clientRequestId() + "|"
                + tool.name() + "|" + write(new TreeMap<>(args))).substring(0, 48);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create tool idempotency key", exception);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception exception) {
            throw new BizException(500, "工具数据无法序列化");
        }
    }

    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (Exception exception) {
            throw new BizException(500, "待确认参数损坏");
        }
    }

    private JsonNode readNode(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ignored) {
            return null;
        }
    }

    private long elapsedMs(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }
}
