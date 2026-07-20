package com.portfolio.csagent.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** RBAC / JWT 配置。发布候选默认启用，零密钥演示使用内置演示账号。 */
@Data
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    /** 启用后 /api/tickets、/api/dashboard 等受 RBAC 保护。 */
    private boolean rbacEnabled = true;

    /** 为 true 时 /api/chat 始终匿名可访问。 */
    private boolean anonymousChat = false;

    /** HS256 签名密钥（生产环境必须通过环境变量覆盖）。 */
    private String jwtSecret = "csagent-dev-jwt-secret-change-me-in-prod!!";

    /** Access token 有效期（分钟）。 */
    private int jwtExpirationMinutes = 15;

    /** 同源浏览器使用 HttpOnly Cookie；CLI 仍可使用 Bearer token。 */
    private String cookieName = "CSAGENT_AUTH";
    private boolean cookieSecure = false;
    private String cookieSameSite = "Strict";
}
