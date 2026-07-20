package com.portfolio.csagent.config;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import com.portfolio.csagent.common.RequestIdContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._:-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader(RequestIdContext.HEADER);
        String requestId = supplied != null && SAFE.matcher(supplied).matches()
                ? supplied
                : UUID.randomUUID().toString();
        RequestIdContext.set(requestId);
        response.setHeader(RequestIdContext.HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            RequestIdContext.clear();
        }
    }
}
