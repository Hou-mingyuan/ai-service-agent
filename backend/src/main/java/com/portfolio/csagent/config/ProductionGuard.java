package com.portfolio.csagent.config;

import java.util.Arrays;

import com.portfolio.csagent.adapter.business.BusinessSystemAdapter;
import com.portfolio.csagent.security.SecurityProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ProductionGuard implements ApplicationRunner {
    private static final String DEV_SECRET = "csagent-dev-jwt-secret-change-me-in-prod!!";

    private final AppProperties appProperties;
    private final SecurityProperties securityProperties;
    private final BusinessSystemAdapter businessAdapter;
    private final Environment environment;

    public ProductionGuard(AppProperties appProperties, SecurityProperties securityProperties,
                           BusinessSystemAdapter businessAdapter, Environment environment) {
        this.appProperties = appProperties;
        this.securityProperties = securityProperties;
        this.businessAdapter = businessAdapter;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (appProperties.getDemo().isEnabled()) {
            return;
        }
        require(securityProperties.isRbacEnabled(), "RBAC must be enabled when APP_DEMO_ENABLED=false");
        require(!securityProperties.isAnonymousChat(),
                "Anonymous chat must be disabled when APP_DEMO_ENABLED=false");
        require(securityProperties.getJwtSecret() != null
                        && securityProperties.getJwtSecret().length() >= 32
                        && !DEV_SECRET.equals(securityProperties.getJwtSecret()),
                "APP_SECURITY_JWT_SECRET must be replaced when APP_DEMO_ENABLED=false");
        require(!appProperties.getCors().getAllowedOrigins().contains("*"),
                "Wildcard CORS is forbidden when APP_DEMO_ENABLED=false");
        require(!businessAdapter.mock(),
                "BUSINESS_ADAPTER=mock is forbidden when APP_DEMO_ENABLED=false");
        require(!"mock".equalsIgnoreCase(appProperties.getLlm().getProvider()),
                "LLM_PROVIDER=mock is forbidden when APP_DEMO_ENABLED=false");
        require(Arrays.asList(environment.getActiveProfiles()).contains("mysql"),
                "The mysql profile is required when APP_DEMO_ENABLED=false");
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
