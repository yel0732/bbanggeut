package com.A501.backend.domain.auth.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Locale;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.auth.dto.request.GoogleAuthRequest;
import com.A501.backend.domain.auth.dto.request.GoogleCompleteRequest;
import com.A501.backend.domain.auth.dto.request.GoogleLinkRequest;
import com.A501.backend.domain.auth.dto.request.LoginRequest;
import com.A501.backend.domain.auth.dto.request.SignupRequest;
import com.A501.backend.domain.auth.entity.Parent;
import com.A501.backend.domain.auth.entity.RefreshToken;
import com.A501.backend.domain.auth.exception.AuthErrorCode;
import com.A501.backend.domain.auth.repository.ParentRepository;
import com.A501.backend.domain.auth.repository.RefreshTokenRepository;
import com.A501.backend.domain.auth.security.GoogleTokenVerifier;
import com.A501.backend.domain.auth.security.GoogleUserInfo;
import com.A501.backend.domain.auth.security.JwtProvider;
import com.A501.backend.domain.auth.security.RefreshTokenProperties;
import com.A501.backend.domain.auth.security.TokenHasher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final int MINIMUM_SIGNUP_AGE = 19;
    private static final int REFRESH_TOKEN_CLEANUP_RETENTION_DAYS = 30;

    private final ParentRepository parentRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenProperties refreshTokenProperties;
    private final GoogleTokenVerifier googleTokenVerifier;

    public boolean isEmailAvailable(String email) {
        return parentRepository.findByEmail(normalizeEmail(email)).isEmpty();
    }

    @Transactional
    public Parent signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        if (parentRepository.findByEmail(email).isPresent()) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (isUnderAge(request.birthDate())) {
            throw new BusinessException(AuthErrorCode.UNDER_AGE);
        }

        Parent parent = Parent.createLocal(
                email,
                passwordEncoder.encode(request.password()),
                request.birthDate());
        return parentRepository.save(parent);
    }

    @Transactional
    public TokenIssueResult login(LoginRequest request) {
        Parent parent = findAuthenticatedParent(normalizeEmail(request.email()), request.password());
        return issueTokens(parent, request.autoLogin());
    }

    @Transactional
    public TokenIssueResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        RefreshToken token = refreshTokenRepository.findByTokenHash(TokenHasher.hash(rawRefreshToken))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        if (token.isRevoked()) {
            log.warn("Refresh token reuse detected for parentId={}", token.getParent().getParentId());
            refreshTokenRepository.findAllByParentAndRevokedFalse(token.getParent())
                    .forEach(RefreshToken::revoke);
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_REUSED);
        }
        if (token.isExpired()) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        token.revoke();
        return issueTokens(token.getParent(), token.isRememberMe());
    }

    @Transactional
    public void logout(Long authenticatedParentId, String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(TokenHasher.hash(rawRefreshToken))
                .filter(token -> token.getParent().getParentId().equals(authenticatedParentId))
                .ifPresent(RefreshToken::revoke);
    }

    @Transactional
    public GoogleAuthResult googleAuth(GoogleAuthRequest request) {
        GoogleUserInfo googleUser = googleTokenVerifier.verify(request.idToken());

        Optional<Parent> byProviderId = parentRepository.findByProviderId(googleUser.providerId());
        if (byProviderId.isPresent()) {
            return GoogleAuthResult.loginSuccess(issueTokens(byProviderId.get(), true));
        }

        Optional<Parent> byEmail = parentRepository.findByEmail(normalizeEmail(googleUser.email()));
        if (byEmail.isPresent() && byEmail.get().hasPassword()) {
            String linkToken = jwtProvider.generateLinkToken(byEmail.get().getParentId(), googleUser.providerId());
            return GoogleAuthResult.linkRequired(linkToken);
        }

        LocalDate resolvedBirthDate = resolveGoogleBirthDate(googleUser);
        if (resolvedBirthDate != null && !isUnderAge(resolvedBirthDate)) {
            Parent parent = parentRepository.save(
                    Parent.createGoogle(normalizeEmail(googleUser.email()), googleUser.providerId(), resolvedBirthDate));
            return GoogleAuthResult.loginSuccess(issueTokens(parent, true));
        }

        String signupToken = jwtProvider.generateSignupToken(googleUser.providerId(), googleUser.email());
        return GoogleAuthResult.signupRequired(signupToken, googleUser.email());
    }

    // 향후 People API로 생년월일을 자동 조회할 확장 지점. 지금은 항상 null을 반환해 SIGNUP_REQUIRED로만 분기한다.
    // 자동 조회를 붙이더라도 클라이언트가 보낸 값이 아닌, 서버가 직접 확보한 값만 신뢰해야 한다(연령 검증 우회 방지).
    private LocalDate resolveGoogleBirthDate(GoogleUserInfo googleUser) {
        return null;
    }

    @Transactional
    public Parent googleComplete(GoogleCompleteRequest request) {
        JwtProvider.SignupTokenClaims claims = jwtProvider.parseSignupToken(request.signupToken());

        if (isUnderAge(request.birthDate())) {
            throw new BusinessException(AuthErrorCode.UNDER_AGE);
        }

        String email = normalizeEmail(claims.email());
        if (parentRepository.findByEmail(email).isPresent()) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }

        Parent parent = Parent.createGoogle(email, claims.providerId(), request.birthDate());
        return parentRepository.save(parent);
    }

    @Transactional
    public TokenIssueResult googleLink(GoogleLinkRequest request) {
        JwtProvider.LinkTokenClaims claims = jwtProvider.parseLinkToken(request.linkToken());

        Parent parent = parentRepository.findById(claims.parentId())
                .filter(p -> !p.isDeleted())
                .filter(Parent::hasPassword)
                .filter(p -> passwordEncoder.matches(request.password(), p.getPasswordHash()))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));

        parent.linkGoogleAccount(claims.providerId());
        return issueTokens(parent, true);
    }

    @Transactional
    public void cleanupExpiredRefreshTokens() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(REFRESH_TOKEN_CLEANUP_RETENTION_DAYS);
        long deletedCount = refreshTokenRepository.deleteByExpiresAtBefore(cutoff);
        log.info("Deleted {} refresh tokens with expiresAt before {}", deletedCount, cutoff);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private Parent findAuthenticatedParent(String email, String rawPassword) {
        return parentRepository.findByEmail(email)
                .filter(parent -> !parent.isDeleted())
                .filter(Parent::hasPassword)
                .filter(parent -> passwordEncoder.matches(rawPassword, parent.getPasswordHash()))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));
    }

    private boolean isUnderAge(LocalDate birthDate) {
        return Period.between(birthDate, LocalDate.now()).getYears() < MINIMUM_SIGNUP_AGE;
    }

    private TokenIssueResult issueTokens(Parent parent, boolean rememberMe) {
        String accessToken = jwtProvider.generateAccessToken(parent);
        long refreshTokenExpiryDays = rememberMe
                ? refreshTokenProperties.rememberMeExpiryDays()
                : refreshTokenProperties.defaultExpiryDays();

        String rawRefreshToken = TokenHasher.generateRawToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(refreshTokenExpiryDays);
        refreshTokenRepository.save(
                RefreshToken.issue(parent, TokenHasher.hash(rawRefreshToken), expiresAt, rememberMe));

        long maxAgeSeconds = refreshTokenExpiryDays * 24 * 60 * 60;
        return new TokenIssueResult(
                parent, accessToken, jwtProvider.getAccessTokenExpirySeconds(), rawRefreshToken, maxAgeSeconds);
    }
}
