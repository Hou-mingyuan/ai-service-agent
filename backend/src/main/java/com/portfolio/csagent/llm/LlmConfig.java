package com.portfolio.csagent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.csagent.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.net.InetAddress;
import java.net.URI;

@Configuration
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    @Bean
    public LlmClient llmClient(AppProperties props, ObjectMapper objectMapper) {
        AppProperties.Llm cfg = props.getLlm();
        if ("mock".equalsIgnoreCase(cfg.getProvider())) {
            log.info("LLM 供应方：mock（离线规则替身，仅用于演示与测试）");
            return new MockLlmClient(objectMapper);
        }
        if ("openai".equalsIgnoreCase(cfg.getProvider())) {
            if (cfg.getApiKey() == null || cfg.getApiKey().isBlank()) {
                throw new IllegalStateException("LLM_API_KEY is required when LLM_PROVIDER=openai");
            }
            validateBaseUrl(cfg);
            log.info("LLM 供应方：openai（model={}, base-url={}）", cfg.getModel(), cfg.getBaseUrl());
            return new OpenAiLlmClient(cfg, objectMapper);
        }
        throw new IllegalStateException("Unsupported LLM_PROVIDER: " + cfg.getProvider());
    }

    private void validateBaseUrl(AppProperties.Llm cfg) {
        URI uri = URI.create(cfg.getBaseUrl());
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null) {
            throw new IllegalStateException("LLM_BASE_URL must be an absolute http(s) URL");
        }
        if (cfg.isAllowPrivateBaseUrl()) {
            return;
        }
        try {
            InetAddress address = InetAddress.getByName(uri.getHost());
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()) {
                throw new IllegalStateException(
                        "LLM_BASE_URL resolves to a private address; set LLM_ALLOW_PRIVATE_BASE_URL=true only for trusted local gateways");
            }
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("LLM_BASE_URL host cannot be resolved", exception);
        }
    }
}
