package com.portfolio.csagent.controller;

import java.time.LocalDate;
import java.util.Map;

import com.portfolio.csagent.common.ApiResponse;
import com.portfolio.csagent.security.CurrentActor;
import com.portfolio.csagent.security.Permission;
import com.portfolio.csagent.service.DashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService dashboardService;
    private final CurrentActor currentActor;

    public DashboardController(DashboardService dashboardService, CurrentActor currentActor) {
        this.dashboardService = dashboardService;
        this.currentActor = currentActor;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('" + Permission.DASHBOARD_READ + "')")
    public ApiResponse<Map<String, Object>> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate effectiveTo = to == null ? LocalDate.now() : to;
        LocalDate effectiveFrom = from == null ? effectiveTo.minusDays(6) : from;
        return ApiResponse.ok(dashboardService.overview(currentActor.require().tenantId(),
                effectiveFrom, effectiveTo));
    }
}
