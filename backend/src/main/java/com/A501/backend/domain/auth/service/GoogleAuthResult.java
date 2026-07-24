package com.A501.backend.domain.auth.service;

public record GoogleAuthResult(
        GoogleAuthOutcome outcome,
        TokenIssueResult tokenIssueResult,
        String signupToken,
        String email,
        String linkToken
) {

    public enum GoogleAuthOutcome {
        LOGIN_SUCCESS,
        SIGNUP_REQUIRED,
        LINK_REQUIRED
    }

    public static GoogleAuthResult loginSuccess(TokenIssueResult tokenIssueResult) {
        return new GoogleAuthResult(GoogleAuthOutcome.LOGIN_SUCCESS, tokenIssueResult, null, null, null);
    }

    public static GoogleAuthResult signupRequired(String signupToken, String email) {
        return new GoogleAuthResult(GoogleAuthOutcome.SIGNUP_REQUIRED, null, signupToken, email, null);
    }

    public static GoogleAuthResult linkRequired(String linkToken) {
        return new GoogleAuthResult(GoogleAuthOutcome.LINK_REQUIRED, null, null, null, linkToken);
    }
}
