package com.portfolio.csagent.service;

import java.util.Map;
import java.util.Set;

/**
 * 工单状态机：集中定义允许的状态流转，保证工单生命周期一致、可测。
 *
 * <pre>
 * OPEN ──→ IN_PROGRESS ──→ PENDING ──→ RESOLVED ──→ CLOSED
 *   │           │             │            │
 *   └───────────┴─────────────┴────────────┴──→ CLOSED（可随时关闭）
 * RESOLVED ──→ IN_PROGRESS（重开）
 * CLOSED 为终态。
 * </pre>
 */
public final class TicketStateMachine {

    public static final String OPEN = "OPEN";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String PENDING = "PENDING";
    public static final String RESOLVED = "RESOLVED";
    public static final String CLOSED = "CLOSED";

    private static final Map<String, Set<String>> ALLOWED = Map.of(
            OPEN, Set.of(IN_PROGRESS, PENDING, RESOLVED, CLOSED),
            IN_PROGRESS, Set.of(PENDING, RESOLVED, CLOSED),
            PENDING, Set.of(IN_PROGRESS, RESOLVED, CLOSED),
            RESOLVED, Set.of(IN_PROGRESS, CLOSED),
            CLOSED, Set.of());

    private TicketStateMachine() {
    }

    public static boolean isValidStatus(String status) {
        return ALLOWED.containsKey(status);
    }

    public static boolean canTransition(String from, String to) {
        if (from == null || to == null) {
            return false;
        }
        Set<String> next = ALLOWED.get(from);
        return next != null && next.contains(to);
    }

    public static boolean isTerminal(String status) {
        return CLOSED.equals(status);
    }
}
