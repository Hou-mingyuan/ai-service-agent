package com.portfolio.csagent.service;

import static com.portfolio.csagent.service.TicketStateMachine.CLOSED;
import static com.portfolio.csagent.service.TicketStateMachine.IN_PROGRESS;
import static com.portfolio.csagent.service.TicketStateMachine.OPEN;
import static com.portfolio.csagent.service.TicketStateMachine.PENDING;
import static com.portfolio.csagent.service.TicketStateMachine.RESOLVED;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TicketStateMachineTest {

    @Test
    @DisplayName("合法流转允许")
    void validTransitions() {
        assertTrue(TicketStateMachine.canTransition(OPEN, IN_PROGRESS));
        assertTrue(TicketStateMachine.canTransition(IN_PROGRESS, PENDING));
        assertTrue(TicketStateMachine.canTransition(PENDING, RESOLVED));
        assertTrue(TicketStateMachine.canTransition(RESOLVED, CLOSED));
        assertTrue(TicketStateMachine.canTransition(OPEN, CLOSED));
        assertTrue(TicketStateMachine.canTransition(RESOLVED, IN_PROGRESS), "已解决可重开");
    }

    @Test
    @DisplayName("非法流转拒绝")
    void invalidTransitions() {
        assertFalse(TicketStateMachine.canTransition(CLOSED, OPEN), "已关闭为终态");
        assertFalse(TicketStateMachine.canTransition(CLOSED, IN_PROGRESS));
        assertFalse(TicketStateMachine.canTransition(OPEN, OPEN));
        assertFalse(TicketStateMachine.canTransition(null, OPEN));
    }

    @Test
    @DisplayName("状态校验与终态判断")
    void statusChecks() {
        assertTrue(TicketStateMachine.isValidStatus(OPEN));
        assertFalse(TicketStateMachine.isValidStatus("FOO"));
        assertTrue(TicketStateMachine.isTerminal(CLOSED));
        assertFalse(TicketStateMachine.isTerminal(OPEN));
    }
}
