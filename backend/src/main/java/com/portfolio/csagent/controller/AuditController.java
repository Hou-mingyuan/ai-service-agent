package com.portfolio.csagent.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.portfolio.csagent.common.ApiResponse;
import com.portfolio.csagent.entity.AuditLog;
import com.portfolio.csagent.security.CurrentActor;
import com.portfolio.csagent.security.Permission;
import com.portfolio.csagent.service.AuditService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
public class AuditController {
    private final AuditService auditService;
    private final CurrentActor currentActor;

    public AuditController(AuditService auditService, CurrentActor currentActor) {
        this.auditService = auditService;
        this.currentActor = currentActor;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permission.AUDIT_READ + "')")
    public ApiResponse<IPage<AuditLog>> page(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "30") int size,
                                             @RequestParam(required = false) String action,
                                             @RequestParam(required = false) String actor) {
        return ApiResponse.ok(auditService.page(currentActor.require().tenantId(), page, size, action, actor));
    }
}
