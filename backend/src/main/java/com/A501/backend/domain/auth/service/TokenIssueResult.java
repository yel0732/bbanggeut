package com.A501.backend.domain.auth.service;

import com.A501.backend.domain.auth.entity.Parent;

public record TokenIssueResult(
        Parent parent,
        String accessToken,
        long accessTokenExpiresInSeconds,
        String rawRefreshToken,
        long refreshTokenMaxAgeSeconds
) {
}
