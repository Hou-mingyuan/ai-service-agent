package com.portfolio.csagent.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 为只读 REST 接口附加合理的 Cache-Control，便于浏览器/CDN 缓存；
 * 动态接口（工单、对话）禁止缓存。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class ApiCacheFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(request, response);

        if (!HttpMethod.GET.matches(request.getMethod())) {
            return;
        }
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            return;
        }
        if (response.containsHeader("Cache-Control")) {
            return;
        }

        if (path.startsWith("/api/catalog") || path.startsWith("/api/faq")) {
            response.setHeader("Cache-Control", "public, max-age=300, stale-while-revalidate=60");
        } else if (path.startsWith("/api/dashboard")) {
            response.setHeader("Cache-Control", "no-store");
        } else if (path.startsWith("/api/tickets")) {
            response.setHeader("Cache-Control", "no-cache, must-revalidate");
        } else if (path.startsWith("/api/health")) {
            response.setHeader("Cache-Control", "no-store");
        } else if (path.startsWith("/api/conversations")) {
            response.setHeader("Cache-Control", "no-store");
        }
    }
}
