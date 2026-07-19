package com.portfolio.csagent.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final SecurityProperties securityProperties;
    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(SecurityProperties securityProperties, JwtAuthFilter jwtAuthFilter) {
        this.securityProperties = securityProperties;
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        if (securityProperties.isRbacEnabled()) {
            http.anonymous(anonymous -> anonymous.disable())
                    .exceptionHandling(ex -> ex
                            .authenticationEntryPoint((request, response, authException) ->
                                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                            .accessDeniedHandler((request, response, accessDeniedException) ->
                                    response.sendError(HttpServletResponse.SC_FORBIDDEN)))
                    .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/health", "/api/auth/**", "/h2-console/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/chat").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/catalog/**", "/api/faq/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/catalog/**").hasAuthority(Permission.CATALOG_WRITE)
                    .requestMatchers("/api/dashboard/**").hasAuthority(Permission.DASHBOARD_READ)
                    .requestMatchers(HttpMethod.POST, "/api/tickets/*/assign").hasAuthority(Permission.TICKET_ASSIGN)
                    .requestMatchers("/api/tickets/**").hasAuthority(Permission.TICKET_READ)
                    .requestMatchers("/api/feedback/**").permitAll()
                    .anyRequest().authenticated());
        } else {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }

        return http.build();
    }
}
