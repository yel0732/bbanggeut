package com.A501.backend.domain.auth.controller;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.A501.backend.domain.auth.dto.request.EmailPatterns;
import com.A501.backend.domain.auth.dto.request.GoogleAuthRequest;
import com.A501.backend.domain.auth.dto.request.GoogleCompleteRequest;
import com.A501.backend.domain.auth.dto.request.LoginRequest;
import com.A501.backend.domain.auth.dto.request.SignupRequest;
import com.A501.backend.domain.auth.dto.response.AuthTokenResponse;
import com.A501.backend.domain.auth.dto.response.EmailCheckResponse;
import com.A501.backend.domain.auth.dto.response.GoogleAuthResponse;
import com.A501.backend.domain.auth.dto.response.SignupResponse;
import com.A501.backend.domain.auth.dto.response.TokenRefreshResponse;
import com.A501.backend.domain.auth.entity.Parent;
import com.A501.backend.domain.auth.security.CookieProperties;
import com.A501.backend.domain.auth.service.AuthService;
import com.A501.backend.domain.auth.service.GoogleAuthResult;
import com.A501.backend.domain.auth.service.TokenIssueResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

@Tag(name = "auth")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";

    private final AuthService authService;
    private final CookieProperties cookieProperties;

    @Operation(summary = "이메일 중복 확인")
    @GetMapping("/email/check")
    public ResponseEntity<EmailCheckResponse> checkEmail(
            @RequestParam @Email @NotBlank @Pattern(regexp = EmailPatterns.WITH_TLD) String email) {
        return ResponseEntity.ok(EmailCheckResponse.of(authService.isEmailAvailable(email)));
    }

    @Operation(summary = "이메일 회원가입")
    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        Parent parent = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(SignupResponse.of(parent));
    }

    @Operation(summary = "이메일 로그인")
    @PostMapping("/login")
    public ResponseEntity<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenIssueResult result = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshTokenCookie(result).toString())
                .body(AuthTokenResponse.of(result.parent(), result.accessToken(), result.accessTokenExpiresInSeconds()));
    }

    @Operation(summary = "토큰 재발급")
    @PostMapping("/token/refresh")
    public ResponseEntity<TokenRefreshResponse> refresh(
            @CookieValue(name = REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken) {
        TokenIssueResult result = authService.refresh(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshTokenCookie(result).toString())
                .body(TokenRefreshResponse.of(result.accessToken(), result.accessTokenExpiresInSeconds()));
    }

    @Operation(summary = "로그아웃")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            Authentication authentication,
            @CookieValue(name = REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken) {
        Long parentId = (Long) authentication.getPrincipal();
        authService.logout(parentId, refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, buildExpiredRefreshTokenCookie().toString())
                .build();
    }

    @Operation(summary = "구글 로그인/가입 분기")
    @PostMapping("/google")
    public ResponseEntity<GoogleAuthResponse> googleAuth(@Valid @RequestBody GoogleAuthRequest request) {
        GoogleAuthResult result = authService.googleAuth(request);
        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok();
        if (result.outcome() == GoogleAuthResult.GoogleAuthOutcome.LOGIN_SUCCESS) {
            responseBuilder.header(HttpHeaders.SET_COOKIE, buildRefreshTokenCookie(result.tokenIssueResult()).toString());
        }
        return responseBuilder.body(GoogleAuthResponse.from(result));
    }

    @Operation(summary = "구글 회원가입 완료(생년월일 입력)")
    @PostMapping("/google/complete")
    public ResponseEntity<SignupResponse> googleComplete(@Valid @RequestBody GoogleCompleteRequest request) {
        Parent parent = authService.googleComplete(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(SignupResponse.of(parent));
    }

    private ResponseCookie buildRefreshTokenCookie(TokenIssueResult result) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, result.rawRefreshToken())
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(Duration.ofSeconds(result.refreshTokenMaxAgeSeconds()))
                .build();
    }

    private ResponseCookie buildExpiredRefreshTokenCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(0)
                .build();
    }
}
