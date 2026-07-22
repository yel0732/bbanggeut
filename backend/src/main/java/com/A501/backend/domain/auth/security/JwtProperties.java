package com.A501.backend.domain.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        long accessTokenExpiryMinutes,
        long signupTokenExpiryMinutes,
        long linkTokenExpiryMinutes
) {
}
