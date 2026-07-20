package com.portfolio.csagent.ws;

import java.util.Map;
import java.util.Arrays;

import com.portfolio.csagent.security.JwtService;
import com.portfolio.csagent.security.Permission;
import com.portfolio.csagent.security.SecurityProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import jakarta.servlet.http.Cookie;

@Component
public class AgentWebSocketAuthInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;
    private final SecurityProperties securityProperties;

    public AgentWebSocketAuthInterceptor(JwtService jwtService, SecurityProperties securityProperties) {
        this.jwtService = jwtService;
        this.securityProperties = securityProperties;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!securityProperties.isRbacEnabled()) {
            return true;
        }
        String token = extractToken(request);
        return jwtService.parseToken(token)
                .filter(p -> p.role().getPermissions().contains(Permission.WS_EVENTS))
                .map(p -> {
                    attributes.put("username", p.username());
                    attributes.put("role", p.role().getId());
                    attributes.put("tenantId", p.tenantId());
                    return true;
                })
                .orElse(false);
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private String extractToken(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String auth = servletRequest.getServletRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (auth != null && auth.startsWith("Bearer ")) {
                return auth.substring(7).trim();
            }
            Cookie[] cookies = servletRequest.getServletRequest().getCookies();
            if (cookies != null) {
                return Arrays.stream(cookies)
                        .filter(cookie -> securityProperties.getCookieName().equals(cookie.getName()))
                        .map(Cookie::getValue)
                        .findFirst()
                        .orElse(null);
            }
        }
        return null;
    }
}
