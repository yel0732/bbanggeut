package com.A501.backend.domain.auth.dto.response;

import com.A501.backend.domain.auth.service.GoogleAuthResult;
import com.A501.backend.domain.auth.service.TokenIssueResult;

public record GoogleAuthResponse(
        String status,
        AuthTokenResponse tokens,
        String signupToken,
        String email,
        String linkToken
) {

    public static GoogleAuthResponse loginSuccess(TokenIssueResult result) {
        return new GoogleAuthResponse(
                GoogleAuthResult.GoogleAuthOutcome.LOGIN_SUCCESS.name(),
                AuthTokenResponse.of(result.parent(), result.accessToken(), result.accessTokenExpiresInSeconds()),
                null, null, null);
    }

    public static GoogleAuthResponse signupRequired(String signupToken, String email) {
        return new GoogleAuthResponse(
                GoogleAuthResult.GoogleAuthOutcome.SIGNUP_REQUIRED.name(), null, signupToken, email, null);
    }

    public static GoogleAuthResponse linkRequired(String linkToken) {
        return new GoogleAuthResponse(
                GoogleAuthResult.GoogleAuthOutcome.LINK_REQUIRED.name(), null, null, null, linkToken);
    }

    public static GoogleAuthResponse from(GoogleAuthResult result) {
        return switch (result.outcome()) {
            case LOGIN_SUCCESS -> loginSuccess(result.tokenIssueResult());
            case SIGNUP_REQUIRED -> signupRequired(result.signupToken(), result.email());
            case LINK_REQUIRED -> linkRequired(result.linkToken());
        };
    }
}
