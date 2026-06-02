package com.secondhand.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
class JwtServiceImpl implements JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    JwtServiceImpl(
            @Value("${app.security.jwt.secret}") String secret,
            @Value("${app.security.jwt.access-token-expiration-minutes}") long expMinutes) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expMinutes * 60 * 1000;
    }

    @Override
    public String createAccessToken(long userId) {
        return createToken(userId, "USER");
    }

    public String createAccessToken(long userId, String role) {
        return createToken(userId, role);
    }

    private String createToken(long userId, String role) {
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString().substring(0, 8))
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    @Override
    public long parseUserId(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    /** 解析 token 中的 role */
    public String parseRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
