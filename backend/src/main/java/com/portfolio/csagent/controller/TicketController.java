package com.portfolio.csagent.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.portfolio.csagent.common.ApiResponse;
import com.portfolio.csagent.common.BizException;
import com.portfolio.csagent.dto.TicketAssignRequest;
import com.portfolio.csagent.dto.TicketCreateRequest;
import com.portfolio.csagent.dto.TicketTransitionRequest;
import com.portfolio.csagent.entity.Ticket;
import com.portfolio.csagent.entity.TicketEvent;
import com.portfolio.csagent.mapper.TicketEventMapper;
import com.portfolio.csagent.mapper.TicketMapper;
import com.portfolio.csagent.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;
    private final TicketMapper ticketMapper;
    private final TicketEventMapper ticketEventMapper;

    public TicketController(TicketService ticketService, TicketMapper ticketMapper,
                            TicketEventMapper ticketEventMapper) {
        this.ticketService = ticketService;
        this.ticketMapper = ticketMapper;
        this.ticketEventMapper = ticketEventMapper;
    }

    @GetMapping
    public ApiResponse<List<Ticket>> list(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) String category,
                                          @RequestParam(defaultValue = "100") int limit) {
        List<Ticket> list = ticketMapper.selectList(Wrappers.<Ticket>lambdaQuery()
                .eq(StringUtils.isNotBlank(status), Ticket::getStatus, status)
                .eq(StringUtils.isNotBlank(category), Ticket::getCategory, category)
                .orderByDesc(Ticket::getId)
                .last("limit " + Math.max(1, limit)));
        return ApiResponse.ok(list);
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        Ticket t = ticketMapper.selectById(id);
        if (t == null) {
            throw new BizException(404, "工单不存在：" + id);
        }
        List<TicketEvent> events = ticketEventMapper.selectList(Wrappers.<TicketEvent>lambdaQuery()
                .eq(TicketEvent::getTicketId, id).orderByAsc(TicketEvent::getId));
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ticket", t);
        map.put("events", events);
        return ApiResponse.ok(map);
    }

    @GetMapping("/{id}/events")
    public ApiResponse<List<TicketEvent>> events(@PathVariable Long id) {
        return ApiResponse.ok(ticketEventMapper.selectList(Wrappers.<TicketEvent>lambdaQuery()
                .eq(TicketEvent::getTicketId, id).orderByAsc(TicketEvent::getId)));
    }

    @PostMapping
    public ApiResponse<Ticket> create(@Valid @RequestBody TicketCreateRequest req) {
        return ApiResponse.ok(ticketService.create(req.getConversationId(), req.getCategory(),
                req.getTitle(), req.getDescription(), req.getPriority(), "HUMAN", req.getCustomer()));
    }

    @PostMapping("/{id}/transition")
    public ApiResponse<Ticket> transition(@PathVariable Long id,
                                          @Valid @RequestBody TicketTransitionRequest req) {
        return ApiResponse.ok(ticketService.transition(
                id, req.getToStatus(), req.getOperator(), req.getNote()));
    }

    @PostMapping("/{id}/assign")
    public ApiResponse<Ticket> assign(@PathVariable Long id,
                                      @Valid @RequestBody TicketAssignRequest req) {
        return ApiResponse.ok(ticketService.assign(id, req.getAssignee()));
    }
}
