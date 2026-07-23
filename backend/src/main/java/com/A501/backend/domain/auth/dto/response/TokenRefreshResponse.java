package com.A501.backend.domain.auth.dto.response;

public record TokenRefreshResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds
) {

    public static TokenRefreshResponse of(String accessToken, long expiresInSeconds) {
        return new TokenRefreshResponse(accessToken, "Bearer", expiresInSeconds);
    }
}
