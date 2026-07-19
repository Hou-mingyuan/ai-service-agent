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

    private final SecurityProperties securityProperties;

    public JwtService(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    public String createToken(String username, Role role) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(securityProperties.getJwtExpirationMinutes() * 60L);
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_ROLE, role.getId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey())
                .compact();
    }

    public Optional<JwtPrincipal> parseToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Role role = Role.fromId(claims.get(CLAIM_ROLE, String.class));
            if (role == null) {
                return Optional.empty();
            }
            return Optional.of(new JwtPrincipal(claims.getSubject(), role));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public boolean hasPermission(String token, String permission) {
        return parseToken(token)
                .map(p -> p.role().getPermissions().contains(permission))
                .orElse(false);
    }

    private SecretKey signingKey() {
        byte[] keyBytes = securityProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public record JwtPrincipal(String username, Role role) {
    }
}
