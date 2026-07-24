package com.A501.backend.domain.report.service;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.report.exception.ReportApiErrorCode;
import com.A501.backend.domain.report.port.ReportParentAccessTokenVerifier;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtReportParentAccessTokenVerifier
        implements ReportParentAccessTokenVerifier {

    public static final String PURPOSE_PARENT_ACCESS = "PARENT_ACCESS";
    private static final String CLAIM_PURPOSE = "purpose";

    private final SecretKey signingKey;

    public JwtReportParentAccessTokenVerifier(
            @Value("${app.jwt.secret}") String secret
    ) {
        this.signingKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void verify(Long parentId, String parentAccessToken) {
        if (parentId == null || parentId < 1
                || parentAccessToken == null
                || parentAccessToken.isBlank()) {
            throw invalidToken();
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(parentAccessToken)
                    .getPayload();
            if (!PURPOSE_PARENT_ACCESS.equals(
                    claims.get(CLAIM_PURPOSE, String.class))
                    || !String.valueOf(parentId).equals(claims.getSubject())) {
                throw invalidToken();
            }
        } catch (JwtException | IllegalArgumentException exception) {
            throw invalidToken();
        }
    }

    private BusinessException invalidToken() {
        return new BusinessException(
                ReportApiErrorCode.INVALID_PARENT_ACCESS_TOKEN);
    }
}
