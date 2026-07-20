package com.portfolio.csagent.adapter.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.csagent.config.AppProperties;
import com.portfolio.csagent.mapper.OrderInfoMapper;
import com.portfolio.csagent.mapper.PolicyMapper;
import com.portfolio.csagent.mapper.ShipmentMapper;
import com.portfolio.csagent.security.RedactionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BusinessAdapterConfig {
    @Bean
    BusinessSystemAdapter businessSystemAdapter(AppProperties properties, ObjectMapper objectMapper,
                                                OrderInfoMapper orderMapper, ShipmentMapper shipmentMapper,
                                                PolicyMapper policyMapper, RedactionService redactionService) {
        String adapter = properties.getBusiness().getAdapter();
        if ("mock".equalsIgnoreCase(adapter)) {
            return new MockBusinessAdapter(orderMapper, shipmentMapper, policyMapper, redactionService);
        }
        if ("http".equalsIgnoreCase(adapter)) {
            return new HttpBusinessAdapter(properties.getBusiness(), objectMapper);
        }
        throw new IllegalStateException("Unsupported BUSINESS_ADAPTER: " + adapter);
    }
}
