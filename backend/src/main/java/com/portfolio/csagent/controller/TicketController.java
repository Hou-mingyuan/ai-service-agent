package com.portfolio.csagent.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.portfolio.csagent.common.ApiResponse;
import com.portfolio.csagent.dto.TicketAssignRequest;
import com.portfolio.csagent.dto.TicketCreateRequest;
import com.portfolio.csagent.dto.TicketReopenRequest;
import com.portfolio.csagent.dto.TicketTransitionRequest;
import com.portfolio.csagent.entity.Ticket;
import com.portfolio.csagent.security.CurrentActor;
import com.portfolio.csagent.security.Permission;
import com.portfolio.csagent.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {
    private final TicketService ticketService;
    private final CurrentActor currentActor;

    public TicketController(TicketService ticketService, CurrentActor currentActor) {
        this.ticketService = ticketService;
        this.currentActor = currentActor;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permission.TICKET_READ + "')")
    public ApiResponse<IPage<Ticket>> list(@RequestParam(required = false) String status,
                                           @RequestParam(required = false) String category,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.ok(ticketService.page(status, category, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permission.TICKET_READ + "')")
    public ApiResponse<TicketService.TicketDetails> detail(@PathVariable Long id) {
        return ApiResponse.ok(ticketService.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permission.TICKET_TRANSITION + "')")
    public ApiResponse<TicketService.CreateResult> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TicketCreateRequest request) {
        return ApiResponse.ok(ticketService.createManual(currentActor.require(), request.getConversationId(),
                request.getCategory(), request.getTitle(), request.getDescription(), request.getPriority(),
                idempotencyKey));
    }

    @PostMapping("/{id}/claim")
    @PreAuthorize("hasAuthority('" + Permission.TICKET_READ + "')")
    public ApiResponse<Ticket> claim(@PathVariable Long id) {
        return ApiResponse.ok(ticketService.claim(id, null));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('" + Permission.TICKET_ASSIGN + "')")
    public ApiResponse<Ticket> assign(@PathVariable Long id,
                                      @Valid @RequestBody TicketAssignRequest request) {
        return ApiResponse.ok(ticketService.claim(id, request.getAssignee()));
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasAuthority('" + Permission.TICKET_TRANSITION + "')")
    public ApiResponse<Ticket> transition(@PathVariable Long id,
                                           @Valid @RequestBody TicketTransitionRequest request) {
        return ApiResponse.ok(ticketService.transition(id, request.getToStatus(), request.getNote(),
                request.getCloseReason()));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAuthority('" + Permission.TICKET_TRANSITION + "')")
    public ApiResponse<Ticket> reopen(@PathVariable Long id,
                                      @Valid @RequestBody TicketReopenRequest request) {
        return ApiResponse.ok(ticketService.reopen(id, request.reason()));
    }
}
