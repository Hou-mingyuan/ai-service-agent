package com.portfolio.csagent.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TENANT = "tenant";
    private static final String CLAIM_DISPLAY_NAME = "displayName";
    private static final String CLAIM_USER_ID = "userId";

    private final SecurityProperties properties;

    public JwtService(SecurityProperties properties) {
        this.properties = properties;
    }

    public String createToken(AuthenticatedUser user) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(properties.getJwtExpirationMinutes() * 60L);
        return Jwts.builder()
                .subject(user.username())
                .claim(CLAIM_USER_ID, user.userId())
                .claim(CLAIM_TENANT, user.tenantId())
                .claim(CLAIM_DISPLAY_NAME, user.displayName())
                .claim(CLAIM_ROLE, user.role().getId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey())
                .compact();
    }

    public Optional<AuthenticatedUser> parseToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser().verifyWith(signingKey()).build()
                    .parseSignedClaims(token).getPayload();
            Role role = Role.fromId(claims.get(CLAIM_ROLE, String.class));
            Number userId = claims.get(CLAIM_USER_ID, Number.class);
            String tenant = claims.get(CLAIM_TENANT, String.class);
            if (role == null || userId == null || tenant == null || tenant.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new AuthenticatedUser(
                    userId.longValue(), tenant, claims.getSubject(),
                    claims.get(CLAIM_DISPLAY_NAME, String.class), role));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(properties.getJwtSecret().getBytes(StandardCharsets.UTF_8));
    }
}
