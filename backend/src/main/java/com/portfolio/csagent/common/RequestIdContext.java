package com.portfolio.csagent.common;

import org.slf4j.MDC;

public final class RequestIdContext {
    public static final String HEADER = "X-Request-ID";
    private static final String MDC_KEY = "requestId";

    private RequestIdContext() {
    }

    public static String current() {
        String value = MDC.get(MDC_KEY);
        return value == null || value.isBlank() ? "system" : value;
    }

    public static void set(String requestId) {
        MDC.put(MDC_KEY, requestId);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
