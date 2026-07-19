package com.portfolio.csagent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.portfolio.csagent.ws.AgentEventSocketHandler;
import com.portfolio.csagent.ws.AgentWebSocketAuthInterceptor;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AgentEventSocketHandler agentEventSocketHandler;
    private final AgentWebSocketAuthInterceptor agentWebSocketAuthInterceptor;

    public WebSocketConfig(AgentEventSocketHandler agentEventSocketHandler,
                           AgentWebSocketAuthInterceptor agentWebSocketAuthInterceptor) {
        this.agentEventSocketHandler = agentEventSocketHandler;
        this.agentWebSocketAuthInterceptor = agentWebSocketAuthInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(agentEventSocketHandler, "/ws/agent")
                .addInterceptors(agentWebSocketAuthInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
