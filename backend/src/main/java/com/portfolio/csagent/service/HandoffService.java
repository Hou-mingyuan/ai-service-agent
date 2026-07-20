package com.portfolio.csagent.service;

import com.portfolio.csagent.entity.Conversation;
import com.portfolio.csagent.entity.Ticket;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HandoffService {
    private final ConversationService conversationService;
    private final TicketService ticketService;

    public HandoffService(ConversationService conversationService, TicketService ticketService) {
        this.conversationService = conversationService;
        this.ticketService = ticketService;
    }

    @Transactional
    public ClaimResult claim(Long conversationId) {
        Conversation conversation = conversationService.claim(conversationId);
        Ticket ticket = ticketService.claimLinkedConversation(conversationId);
        return new ClaimResult(conversation, ticket);
    }

    @Transactional
    public InitiateResult initiate(Conversation conversation, String reason, String category,
                                   String title, String description, String priority, String clientRequestId) {
        Conversation pending = conversationService.requestHandoff(conversation, reason);
        TicketService.CreateResult ticket = ticketService.createHandoff(pending, category, title,
                description, priority, clientRequestId);
        return new InitiateResult(pending, ticket.ticket(), ticket.replayed());
    }

    public record ClaimResult(Conversation conversation, Ticket ticket) {
    }

    public record InitiateResult(Conversation conversation, Ticket ticket, boolean replayed) {
    }
}
