package com.portfolio.csagent.config;

import com.portfolio.csagent.adapter.business.BusinessSystemAdapter;
import com.portfolio.csagent.security.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductionGuardTest {
    @Test
    void acceptsRealAdaptersAndRejectsEachUnsafeProductionTransition() {
        AppProperties app = new AppProperties();
        app.getDemo().setEnabled(false);
        app.getLlm().setProvider("openai");
        app.getCors().setAllowedOrigins("https://support.example.test");
        SecurityProperties security = new SecurityProperties();
        security.setRbacEnabled(true);
        security.setAnonymousChat(false);
        security.setJwtSecret("a-unique-production-secret-with-32-characters");
        BusinessSystemAdapter adapter = mock(BusinessSystemAdapter.class);
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("mysql");
        ProductionGuard guard = new ProductionGuard(app, security, adapter, env);
        assertDoesNotThrow(() -> guard.run(null));

        security.setRbacEnabled(false);
        assertThrows(IllegalStateException.class, () -> guard.run(null));
        security.setRbacEnabled(true);
        security.setAnonymousChat(true);
        assertThrows(IllegalStateException.class, () -> guard.run(null));
        security.setAnonymousChat(false);
        security.setJwtSecret("short");
        assertThrows(IllegalStateException.class, () -> guard.run(null));
        security.setJwtSecret("a-unique-production-secret-with-32-characters");
        app.getCors().setAllowedOrigins("*");
        assertThrows(IllegalStateException.class, () -> guard.run(null));
        app.getCors().setAllowedOrigins("https://support.example.test");
        when(adapter.mock()).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> guard.run(null));
        when(adapter.mock()).thenReturn(false);
        app.getLlm().setProvider("mock");
        assertThrows(IllegalStateException.class, () -> guard.run(null));
        app.getLlm().setProvider("openai");
        env.setActiveProfiles("test");
        assertThrows(IllegalStateException.class, () -> guard.run(null));
        env.setActiveProfiles("mysql");
        assertDoesNotThrow(() -> guard.run(null));
    }
}
