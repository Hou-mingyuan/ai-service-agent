package com.portfolio.csagent.controller;

import java.util.List;

import com.portfolio.csagent.agent.tool.ToolRegistry;
import com.portfolio.csagent.agent.tool.ToolResult;
import com.portfolio.csagent.common.ApiResponse;
import com.portfolio.csagent.entity.ToolExecution;
import com.portfolio.csagent.security.Permission;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tool-executions")
public class ToolExecutionController {
    private final ToolRegistry toolRegistry;

    public ToolExecutionController(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permission.TOOL_READ + "')")
    public ApiResponse<List<ToolExecution>> list(@RequestParam Long conversationId) {
        return ApiResponse.ok(toolRegistry.list(conversationId));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('" + Permission.TOOL_SENSITIVE + "')")
    public ApiResponse<ToolResult> confirm(@PathVariable Long id) {
        return ApiResponse.ok(toolRegistry.confirm(id));
    }
}
