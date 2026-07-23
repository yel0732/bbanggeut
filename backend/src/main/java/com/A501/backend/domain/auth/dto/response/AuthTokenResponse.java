package com.A501.backend.domain.auth.dto.response;

import com.A501.backend.domain.auth.entity.Parent;

public record AuthTokenResponse(
        Long parentId,
        String email,
        String accessToken,
        String tokenType,
        long expiresInSeconds
) {

    public static AuthTokenResponse of(Parent parent, String accessToken, long expiresInSeconds) {
        return new AuthTokenResponse(parent.getParentId(), parent.getEmail(), accessToken, "Bearer", expiresInSeconds);
    }
}
