package com.portfolio.csagent.common;

/** Stable REST envelope with a request id for support and audit correlation. */
public record ApiResponse<T>(int code, String message, T data, String requestId) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data, RequestIdContext.current());
    }

    public static <T> ApiResponse<T> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null, RequestIdContext.current());
    }
}
