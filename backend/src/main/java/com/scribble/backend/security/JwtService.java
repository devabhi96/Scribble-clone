package com.scribble.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class JwtService {

    public enum Role { GUEST, USER }
    public record VerifiedIdentity(String subject, Role role) {}

    private final SecretKey key;
    private final long guestExpirationMinutes;
    private final long userExpirationMinutes;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.guest-expiration-minutes}") long guestExpirationMinutes,
            @Value("${jwt.user-expiration-minutes}") long userExpirationMinutes) {
        this.key = Keys.hmacShaKeyFor(pad(secret).getBytes(StandardCharsets.UTF_8));
        this.guestExpirationMinutes = guestExpirationMinutes;
        this.userExpirationMinutes = userExpirationMinutes;
    }

    public String generateGuestToken(String guestId) {
        return build(guestId, Role.GUEST, guestExpirationMinutes);
    }

    public String generateUserToken(String userId) {
        return build(userId, Role.USER, userExpirationMinutes);
    }

    private String build(String subject, Role role, long minutes) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(minutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    public VerifiedIdentity verify(String token) throws JwtException {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        String role = claims.get("role", String.class);
        return new VerifiedIdentity(claims.getSubject(), Role.valueOf(role));
    }

    public boolean isExpired(JwtException e) {
        return e instanceof ExpiredJwtException;
    }

    private String pad(String secret) {
        StringBuilder sb = new StringBuilder(secret);
        while (sb.length() < 32) sb.append(secret);
        return sb.substring(0, Math.max(32, secret.length()));
    }
}