package com.portfolio.csagent.controller;

import com.portfolio.csagent.common.ApiResponse;
import com.portfolio.csagent.common.BizException;
import com.portfolio.csagent.dto.LoginRequest;
import com.portfolio.csagent.dto.LoginResponse;
import com.portfolio.csagent.security.DemoAuthService;
import com.portfolio.csagent.security.JwtService;
import com.portfolio.csagent.security.SecurityProperties;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final DemoAuthService demoAuthService;
    private final JwtService jwtService;
    private final SecurityProperties securityProperties;

    public AuthController(DemoAuthService demoAuthService, JwtService jwtService,
                          SecurityProperties securityProperties) {
        this.demoAuthService = demoAuthService;
        this.jwtService = jwtService;
        this.securityProperties = securityProperties;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        if (!securityProperties.isRbacEnabled()) {
            throw new BizException(503, "RBAC 未启用，无需登录（app.security.rbac-enabled=false）");
        }
        DemoAuthService.DemoUser user = demoAuthService.authenticate(request.getUsername(), request.getPassword())
                .orElseThrow(() -> new BizException(401, "用户名或密码错误"));
        String token = jwtService.createToken(user.username(), user.role());
        return ApiResponse.ok(new LoginResponse(
                token,
                "Bearer",
                user.role().getId(),
                securityProperties.getJwtExpirationMinutes()));
    }
}
