package com.portfolio.csagent.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** RBAC / JWT 配置。默认关闭鉴权以保持 Hub Mock 零登录演示兼容。 */
@Data
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    /** 启用后 /api/tickets、/api/dashboard 等受 RBAC 保护。 */
    private boolean rbacEnabled = false;

    /** 为 true 时 /api/chat 始终匿名可访问。 */
    private boolean anonymousChat = true;

    /** HS256 签名密钥（生产环境必须通过环境变量覆盖）。 */
    private String jwtSecret = "csagent-dev-jwt-secret-change-me-in-prod!!";

    /** Access token 有效期（分钟）。 */
    private int jwtExpirationMinutes = 15;
}
