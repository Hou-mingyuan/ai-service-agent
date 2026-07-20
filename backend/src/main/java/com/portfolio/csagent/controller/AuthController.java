package com.portfolio.csagent.controller;

import java.time.Duration;
import java.util.Map;

import com.portfolio.csagent.common.ApiResponse;
import com.portfolio.csagent.common.BizException;
import com.portfolio.csagent.config.AppProperties;
import com.portfolio.csagent.dto.LoginRequest;
import com.portfolio.csagent.dto.LoginResponse;
import com.portfolio.csagent.security.AuthenticatedUser;
import com.portfolio.csagent.security.CurrentActor;
import com.portfolio.csagent.security.JwtService;
import com.portfolio.csagent.security.SecurityProperties;
import com.portfolio.csagent.security.UserService;
import com.portfolio.csagent.service.AuditService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;
    private final JwtService jwtService;
    private final SecurityProperties securityProperties;
    private final AppProperties appProperties;
    private final CurrentActor currentActor;
    private final AuditService auditService;

    public AuthController(UserService userService, JwtService jwtService, SecurityProperties securityProperties,
                          AppProperties appProperties, CurrentActor currentActor, AuditService auditService) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.securityProperties = securityProperties;
        this.appProperties = appProperties;
        this.currentActor = currentActor;
        this.auditService = auditService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                             HttpServletResponse response) {
        AuthenticatedUser user;
        try {
            user = userService.authenticate(request.getUsername(), request.getPassword());
        } catch (BizException exception) {
            auditService.recordAnonymous(appProperties.getDemo().getTenantId(), request.getUsername(),
                    "AUTH_LOGIN", "DENIED", Map.of("reason", "INVALID_CREDENTIALS"));
            throw exception;
        }
        String token = jwtService.createToken(user);
        response.addHeader(HttpHeaders.SET_COOKIE, authCookie(token, false).toString());
        auditService.record(user, "AUTH_LOGIN", "AUTH", user.username(), "SUCCESS", null,
                Map.of("role", user.role().getId()));
        return ApiResponse.ok(LoginResponse.from(user, token, securityProperties.getJwtExpirationMinutes()));
    }

    @GetMapping("/me")
    public ApiResponse<LoginResponse> me() {
        AuthenticatedUser user = currentActor.require();
        return ApiResponse.ok(LoginResponse.from(user, null, securityProperties.getJwtExpirationMinutes()));
    }

    /** Public bootstrap endpoint: anonymous state is a successful null response, avoiding expected 401 noise. */
    @GetMapping("/session")
    public ApiResponse<LoginResponse> session() {
        return ApiResponse.ok(currentActor.optional()
                .map(user -> LoginResponse.from(user, null, securityProperties.getJwtExpirationMinutes()))
                .orElse(null));
    }

    @GetMapping("/csrf")
    public ApiResponse<Map<String, String>> csrf(CsrfToken token) {
        return ApiResponse.ok(Map.of("headerName", token.getHeaderName(), "token", token.getToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletResponse response) {
        AuthenticatedUser user = currentActor.require();
        auditService.record(user, "AUTH_LOGOUT", "AUTH", user.username(), "SUCCESS", null, Map.of());
        response.addHeader(HttpHeaders.SET_COOKIE, authCookie("", true).toString());
        return ApiResponse.ok();
    }

    private ResponseCookie authCookie(String token, boolean delete) {
        return ResponseCookie.from(securityProperties.getCookieName(), token)
                .httpOnly(true)
                .secure(securityProperties.isCookieSecure())
                .sameSite(securityProperties.getCookieSameSite())
                .path("/")
                .maxAge(delete ? Duration.ZERO
                        : Duration.ofMinutes(securityProperties.getJwtExpirationMinutes()))
                .build();
    }
}
