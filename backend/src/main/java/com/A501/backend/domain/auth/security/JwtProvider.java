package com.A501.backend.domain.auth.security;

import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.auth.entity.Parent;
import com.A501.backend.domain.auth.exception.AuthErrorCode;

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
    private static final String CLAIM_PROVIDER_ID = "providerId";
    private static final String PURPOSE_ACCESS = "ACCESS";
    private static final String PURPOSE_SIGNUP = "SIGNUP";
    private static final String PURPOSE_LINK = "LINK";

    private final JwtProperties jwtProperties;

    public record SignupTokenClaims(String providerId, String email) {
    }

    public record LinkTokenClaims(Long parentId, String providerId) {
    }

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

    public String generateSignupToken(String providerId, String email) {
        return generateShortLivedToken(
                providerId, email, null, PURPOSE_SIGNUP, jwtProperties.signupTokenExpiryMinutes());
    }

    public String generateLinkToken(Long parentId, String providerId) {
        return generateShortLivedToken(
                String.valueOf(parentId), null, providerId, PURPOSE_LINK, jwtProperties.linkTokenExpiryMinutes());
    }

    public SignupTokenClaims parseSignupToken(String token) {
        Claims claims = parseWithPurpose(token, PURPOSE_SIGNUP, AuthErrorCode.INVALID_SIGNUP_TOKEN);
        return new SignupTokenClaims(claims.getSubject(), claims.get(CLAIM_EMAIL, String.class));
    }

    public LinkTokenClaims parseLinkToken(String token) {
        Claims claims = parseWithPurpose(token, PURPOSE_LINK, AuthErrorCode.INVALID_LINK_TOKEN);
        try {
            return new LinkTokenClaims(Long.valueOf(claims.getSubject()), claims.get(CLAIM_PROVIDER_ID, String.class));
        } catch (NumberFormatException e) {
            throw new BusinessException(AuthErrorCode.INVALID_LINK_TOKEN);
        }
    }

    private String generateShortLivedToken(
            String subject, String email, String providerId, String purpose, long expiryMinutes) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(expiryMinutes * 60);

        var builder = Jwts.builder()
                .subject(subject)
                .claim(CLAIM_PURPOSE, purpose)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry));
        if (email != null) {
            builder.claim(CLAIM_EMAIL, email);
        }
        if (providerId != null) {
            builder.claim(CLAIM_PROVIDER_ID, providerId);
        }
        return builder.signWith(signingKey()).compact();
    }

    private Claims parseWithPurpose(String token, String expectedPurpose, AuthErrorCode errorCode) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(errorCode);
        }

        if (!expectedPurpose.equals(claims.get(CLAIM_PURPOSE, String.class))) {
            throw new BusinessException(errorCode);
        }
        return claims;
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtProperties.secret().getBytes());
    }
}
