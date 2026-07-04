package com.portfolio.csagent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.portfolio.csagent.ws.AgentEventSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AgentEventSocketHandler agentEventSocketHandler;

    public WebSocketConfig(AgentEventSocketHandler agentEventSocketHandler) {
        this.agentEventSocketHandler = agentEventSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(agentEventSocketHandler, "/ws/agent")
                .setAllowedOriginPatterns("*");
    }
}
