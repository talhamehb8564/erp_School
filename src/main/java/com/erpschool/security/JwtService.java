package com.erpschool.security;

import com.erpschool.config.AppProperties;
import com.erpschool.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_TENANT = "tenantId";
    public static final String CLAIM_USERNAME = "username";
    public static final String CLAIM_TYPE = "typ";
    public static final String TYPE_ACCESS = "access";

    private final SecretKey key;
    private final long accessTtlSeconds;

    public JwtService(AppProperties properties) {
        byte[] secretBytes = properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.accessTtlSeconds = properties.getJwt().getAccessTokenTtlSeconds();
    }

    public String createAccessToken(User user) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(user.getId().toString())
                .claim(CLAIM_USERNAME, user.getUsername())
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTtlSeconds)));
        if (user.getTenantId() != null) {
            builder.claim(CLAIM_TENANT, user.getTenantId().toString());
        }
        return builder.signWith(key).compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValidAccessToken(String token) {
        try {
            Claims claims = parse(token);
            return TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class));
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public UUID userId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    public UUID tenantId(Claims claims) {
        String value = claims.get(CLAIM_TENANT, String.class);
        return value == null ? null : UUID.fromString(value);
    }

    public long getAccessTtlSeconds() {
        return accessTtlSeconds;
    }
}
