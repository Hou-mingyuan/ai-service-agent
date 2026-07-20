package com.portfolio.csagent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

import com.portfolio.csagent.config.AppProperties;
import com.portfolio.csagent.security.SecurityProperties;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties({AppProperties.class, SecurityProperties.class})
@MapperScan("com.portfolio.csagent.mapper")
public class CsAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(CsAgentApplication.class, args);
    }
}
