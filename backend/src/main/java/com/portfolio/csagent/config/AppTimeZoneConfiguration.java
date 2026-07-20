package com.portfolio.csagent.config;

import java.time.ZoneId;
import java.util.TimeZone;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppTimeZoneConfiguration {
    private final AppProperties properties;

    public AppTimeZoneConfiguration(AppProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void configureDefaultTimeZone() {
        ZoneId zoneId = ZoneId.of(properties.getTimeZone());
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId));
    }
}
