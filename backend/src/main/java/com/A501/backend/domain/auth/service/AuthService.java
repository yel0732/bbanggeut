package com.A501.backend.domain.auth.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
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
import com.A501.backend.domain.auth.exception.PinErrorCode;
import com.A501.backend.domain.auth.repository.ParentRepository;
import com.A501.backend.domain.auth.repository.RefreshTokenRepository;
import com.A501.backend.domain.auth.security.GoogleTokenVerifier;
import com.A501.backend.domain.auth.security.GoogleUserInfo;
import com.A501.backend.domain.auth.security.JwtProvider;
import com.A501.backend.domain.auth.security.RefreshTokenProperties;
import com.A501.backend.domain.auth.security.TokenHasher;
import com.A501.backend.domain.child.service.ChildService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final int MINIMUM_SIGNUP_AGE = 19;
    private static final int REFRESH_TOKEN_CLEANUP_RETENTION_DAYS = 30;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ParentRepository parentRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenProperties refreshTokenProperties;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final PinService pinService;
    private final ChildService childService;

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

    // 재사용 감지 시 다른 활성 토큰을 전부 폐기하는 부분이 BusinessException으로 인한
    // 트랜잭션 롤백에 같이 취소되지 않도록, 이 메서드에서 던지는 예외는 롤백 대상에서 제외한다.
    @Transactional(noRollbackFor = BusinessException.class)
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
    public void changePassword(
            Long parentId, String currentPassword, String newPassword, String newPasswordConfirm,
            String currentRawRefreshToken) {
        Parent parent = getParent(parentId);
        if (!parent.hasPassword()) {
            throw new BusinessException(AuthErrorCode.PASSWORD_NOT_SET);
        }
        if (!passwordEncoder.matches(currentPassword, parent.getPasswordHash())) {
            throw new BusinessException(AuthErrorCode.CURRENT_PASSWORD_MISMATCH);
        }
        if (!newPassword.equals(newPasswordConfirm)) {
            throw new BusinessException(AuthErrorCode.NEW_PASSWORD_CONFIRMATION_MISMATCH);
        }

        parent.changePassword(passwordEncoder.encode(newPassword));
        // TODO: 새 비밀번호가 기존 비밀번호와 동일한 경우의 허용/거부 정책 미정 — 지금은 허용.
        revokeOtherSessions(parent, currentRawRefreshToken);
    }

    // 비밀번호를 바꾼 이 세션은 유지하고, 다른 기기/브라우저의 세션은 전부 로그아웃시킨다.
    // 현재 세션의 refreshToken을 식별할 수 없는 경우(쿠키 없음)는 안전한 쪽으로 전체 폐기한다.
    private void revokeOtherSessions(Parent parent, String currentRawRefreshToken) {
        String currentTokenHash = (currentRawRefreshToken == null || currentRawRefreshToken.isBlank())
                ? null
                : TokenHasher.hash(currentRawRefreshToken);
        refreshTokenRepository.findAllByParentAndRevokedFalse(parent).stream()
                .filter(token -> !token.getTokenHash().equals(currentTokenHash))
                .forEach(RefreshToken::revoke);
    }

    // 회원 탈퇴 — 소프트 삭제(deleted_at 기록)만 하고, 여러 도메인에 걸친 실제 데이터 파기는
    // 후속 비동기 작업으로 남긴다. PIN이 설정된 계정은 PIN 재인증을 요구하고, PIN 미설정 계정은
    // 애초에 다른 기능을 쓸 수 없는 계정이라 바로 탈퇴를 허용한다.
    // noRollbackFor: 이 메서드가 트랜잭션을 먼저 열어 verifyPin이 여기 합류하므로, 없으면 PIN 실패
    // 카운트 증가가 롤백에 같이 취소된다(deleteChild/PinService에서 겪은 것과 같은 트랜잭션 경계 문제).
    @Transactional(noRollbackFor = BusinessException.class)
    public void withdraw(Long parentId, String pin) {
        Parent parent = getParent(parentId);
        if (parent.hasPin()) {
            // 형식(4자리 숫자) 검증을 verifyPin보다 먼저 한다 — null/빈값/형식오류를 400으로 구분하고,
            // 형식이 틀린 입력이 PIN 실패 카운트(잠금)를 소모하지 않게 하기 위함(verifyPin을 타지 않음).
            // DTO 애노테이션으로 못 하는 이유: PIN 미설정 계정은 pin을 아예 안 보내므로 선택값이어야 한다.
            if (pin == null || !pin.matches("\\d{4}")) {
                throw new BusinessException(PinErrorCode.PIN_INVALID_FORMAT);
            }
            pinService.verifyPin(parentId, pin);
        }

        parent.withdraw(LocalDateTime.now(KST));
        childService.softDeleteChildrenOf(parentId);
        refreshTokenRepository.findAllByParentAndRevokedFalse(parent).forEach(RefreshToken::revoke);
    }

    @Transactional
    public GoogleAuthResult googleAuth(GoogleAuthRequest request) {
        GoogleUserInfo googleUser = googleTokenVerifier.verify(request.idToken());

        // 탈퇴(소프트 삭제)된 계정은 걸러낸다 — 거르지 않으면 탈퇴한 구글 사용자가 재로그인으로 부활한다.
        Optional<Parent> byProviderId = parentRepository.findByProviderId(googleUser.providerId())
                .filter(p -> !p.isDeleted());
        if (byProviderId.isPresent()) {
            return GoogleAuthResult.loginSuccess(issueTokens(byProviderId.get(), true));
        }

        Optional<Parent> byEmail = parentRepository.findByEmail(normalizeEmail(googleUser.email()))
                .filter(p -> !p.isDeleted());
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

    // 소프트 삭제된 Parent는 걸러낸다 — PinService.getParent와 동일한 이유(만료 전 access
    // token으로 탈퇴 계정이 인증 필요 API를 계속 쓸 수 있게 되는 걸 막기 위함).
    private Parent getParent(Long parentId) {
        return parentRepository.findById(parentId)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.PARENT_NOT_FOUND));
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
