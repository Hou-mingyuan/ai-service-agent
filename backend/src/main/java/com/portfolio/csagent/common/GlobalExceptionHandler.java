package com.portfolio.csagent.common;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.firewall.RequestRejectedException;
import jakarta.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ObjectMapper objectMapper;

    public GlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @ExceptionHandler(BizException.class)
    public ResponseEntity<?> handleBiz(BizException ex, HttpServletRequest request) {
        int status = ex.getCode() >= 400 && ex.getCode() <= 599 ? ex.getCode() : 400;
        return error(request, status, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .distinct()
                .collect(Collectors.joining("；"));
        return error(request, 400, 400, message.isBlank() ? "参数校验失败" : message);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<?> handleMalformed(Exception ex, HttpServletRequest request) {
        return error(request, 400, 400, "请求格式不正确");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleConflict(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.info("Data conflict requestId={}", RequestIdContext.current());
        return error(request, HttpStatus.CONFLICT.value(), 409, "请求与现有数据冲突，请刷新后重试");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return error(request, HttpStatus.FORBIDDEN.value(), 403, "无权执行该操作");
    }

    @ExceptionHandler(RequestRejectedException.class)
    public ResponseEntity<?> handleRejectedRequest(RequestRejectedException ex, HttpServletRequest request) {
        log.info("Rejected malformed request requestId={}", RequestIdContext.current());
        return error(request, HttpStatus.BAD_REQUEST.value(), 400, "请求格式不正确");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleOther(Exception ex, HttpServletRequest request) {
        log.error("Unhandled request failure requestId={}", RequestIdContext.current(), ex);
        return error(request, HttpStatus.INTERNAL_SERVER_ERROR.value(), 500, "服务器暂时无法处理请求");
    }

    private ResponseEntity<?> error(HttpServletRequest request, int status, int code, String message) {
        ApiResponse<Void> body = ApiResponse.error(code, message);
        String accept = request.getHeader("Accept");
        if ("/api/chat".equals(request.getRequestURI()) && accept != null
                && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            try {
                String event = "event:error\ndata:" + objectMapper.writeValueAsString(body) + "\n\n";
                return ResponseEntity.status(status).contentType(MediaType.TEXT_EVENT_STREAM).body(event);
            } catch (Exception ignored) {
                // Fall through to JSON only if serialization unexpectedly fails.
            }
        }
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
