package com.portfolio.csagent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.portfolio.csagent.ws.AgentEventSocketHandler;
import com.portfolio.csagent.ws.AgentWebSocketAuthInterceptor;
import com.portfolio.csagent.config.AppProperties;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AgentEventSocketHandler agentEventSocketHandler;
    private final AgentWebSocketAuthInterceptor agentWebSocketAuthInterceptor;
    private final AppProperties appProperties;

    public WebSocketConfig(AgentEventSocketHandler agentEventSocketHandler,
                           AgentWebSocketAuthInterceptor agentWebSocketAuthInterceptor,
                           AppProperties appProperties) {
        this.agentEventSocketHandler = agentEventSocketHandler;
        this.agentWebSocketAuthInterceptor = agentWebSocketAuthInterceptor;
        this.appProperties = appProperties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(agentEventSocketHandler, "/ws/events")
                .addInterceptors(agentWebSocketAuthInterceptor)
                .setAllowedOrigins(appProperties.getCors().getAllowedOrigins().split(","));
    }
}
