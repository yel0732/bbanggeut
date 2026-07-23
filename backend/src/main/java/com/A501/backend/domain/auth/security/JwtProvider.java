package com.A501.backend.domain.auth.security;

import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.A501.backend.domain.auth.entity.Parent;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtProvider {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_PURPOSE = "purpose";
    private static final String PURPOSE_ACCESS = "ACCESS";

    private final JwtProperties jwtProperties;

    public String generateAccessToken(Parent parent) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(getAccessTokenExpirySeconds());

        return Jwts.builder()
                .subject(String.valueOf(parent.getParentId()))
                .claim(CLAIM_EMAIL, parent.getEmail())
                .claim(CLAIM_PURPOSE, PURPOSE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey())
                .compact();
    }

    public long getAccessTokenExpirySeconds() {
        return jwtProperties.accessTokenExpiryMinutes() * 60;
    }

    // 서명·만료·purpose 검증에 실패하면 예외 대신 null을 반환해 필터가 미인증으로 처리하게 한다.
    public Long parseAccessToken(String token) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }

        if (!PURPOSE_ACCESS.equals(claims.get(CLAIM_PURPOSE, String.class))) {
            return null;
        }
        return Long.valueOf(claims.getSubject());
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtProperties.secret().getBytes());
    }
}
