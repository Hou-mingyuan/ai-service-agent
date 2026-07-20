package com.portfolio.csagent.controller;

import java.util.List;

import com.portfolio.csagent.common.ApiResponse;
import com.portfolio.csagent.security.CurrentActor;
import com.portfolio.csagent.security.Permission;
import com.portfolio.csagent.service.RealtimeEventService;
import com.portfolio.csagent.ws.RealtimeEnvelope;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class RealtimeEventController {
    private final RealtimeEventService eventService;
    private final CurrentActor currentActor;

    public RealtimeEventController(RealtimeEventService eventService, CurrentActor currentActor) {
        this.eventService = eventService;
        this.currentActor = currentActor;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permission.WS_EVENTS + "')")
    public ApiResponse<List<RealtimeEnvelope>> replay(@RequestParam(defaultValue = "0") long afterId,
                                                       @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(eventService.replay(currentActor.require(), afterId, limit));
    }
}
