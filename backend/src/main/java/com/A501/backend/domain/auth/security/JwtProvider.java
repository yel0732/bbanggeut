package com.A501.backend.domain.auth.security;

import java.security.Key;
import java.time.Instant;
import java.util.Date;

import org.springframework.stereotype.Component;

import com.A501.backend.domain.auth.entity.Parent;

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

    private Key signingKey() {
        return Keys.hmacShaKeyFor(jwtProperties.secret().getBytes());
    }
}
