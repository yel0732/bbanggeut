package com.A501.backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.common.storage.ProfileImageService;
import com.A501.backend.domain.auth.dto.request.GoogleAuthRequest;
import com.A501.backend.domain.auth.dto.request.GoogleCompleteRequest;
import com.A501.backend.domain.auth.dto.request.GoogleLinkRequest;
import com.A501.backend.domain.auth.dto.request.LoginRequest;
import com.A501.backend.domain.auth.dto.request.SignupRequest;
import com.A501.backend.domain.auth.entity.AuthProvider;
import com.A501.backend.domain.auth.entity.Parent;
import com.A501.backend.domain.auth.entity.RefreshToken;
import com.A501.backend.domain.auth.exception.AuthErrorCode;
import com.A501.backend.domain.auth.repository.ParentRepository;
import com.A501.backend.domain.auth.repository.RefreshTokenRepository;
import com.A501.backend.domain.auth.security.GoogleTokenVerifier;
import com.A501.backend.domain.auth.security.GoogleUserInfo;
import com.A501.backend.domain.auth.security.JwtProvider;
import com.A501.backend.domain.auth.exception.PinErrorCode;
import com.A501.backend.domain.auth.security.RefreshTokenProperties;
import com.A501.backend.domain.auth.security.TokenHasher;
import com.A501.backend.domain.child.service.ChildService;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final RefreshTokenProperties REFRESH_TOKEN_PROPERTIES = new RefreshTokenProperties(60, 1);

    @Mock
    private ParentRepository parentRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private GoogleTokenVerifier googleTokenVerifier;

    @Mock
    private PinService pinService;

    @Mock
    private ChildService childService;

    @Mock
    private ProfileImageService profileImageService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        // ParentFinder는 목이 아니라 실제 구현을 끼운다 — 소프트 삭제 필터가 실제로 도는지까지
        // 이 테스트가 계속 검증하도록 하기 위함이다.
        authService = new AuthService(parentRepository, new ParentFinder(parentRepository), refreshTokenRepository,
                passwordEncoder, jwtProvider, REFRESH_TOKEN_PROPERTIES, googleTokenVerifier, pinService, childService,
                profileImageService);
    }

    @Test
    void isEmailAvailable_returnsTrueWhenNoExistingAccount() {
        when(parentRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());

        assertThat(authService.isEmailAvailable("new@example.com")).isTrue();
    }

    @Test
    void isEmailAvailable_returnsFalseWhenAccountExists() {
        Parent existing = Parent.createLocal("taken@example.com", "hash", LocalDate.of(1990, 1, 1));
        when(parentRepository.findByEmail("taken@example.com")).thenReturn(Optional.of(existing));

        assertThat(authService.isEmailAvailable("taken@example.com")).isFalse();
    }

    @Test
    void signup_createsAccount() {
        SignupRequest request = new SignupRequest(
                "new@example.com", "Abcdef1!", LocalDate.now().minusYears(20));
        when(parentRepository.findByEmail(request.email())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(request.password())).thenReturn("hashed-password");
        when(parentRepository.save(any(Parent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Parent result = authService.signup(request);

        verify(parentRepository).save(any(Parent.class));
        assertThat(result.getEmail()).isEqualTo("new@example.com");
    }

    @Test
    void signup_throwsWhenEmailAlreadyExists() {
        SignupRequest request = new SignupRequest(
                "taken@example.com", "Abcdef1!", LocalDate.now().minusYears(20));
        Parent existing = Parent.createLocal("taken@example.com", "hash", LocalDate.of(1990, 1, 1));
        when(parentRepository.findByEmail(request.email())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.EMAIL_ALREADY_EXISTS));
    }

    @Test
    void signup_throwsWhenUnderMinimumAge() {
        SignupRequest request = new SignupRequest(
                "young@example.com", "Abcdef1!", LocalDate.now().minusYears(18));
        when(parentRepository.findByEmail(request.email())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.UNDER_AGE));
    }

    @Test
    void login_issuesShortLivedRefreshTokenWhenAutoLoginUnchecked() {
        LoginRequest request = new LoginRequest("parent@example.com", "Abcdef1!", false);
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        when(parentRepository.findByEmail(request.email())).thenReturn(Optional.of(parent));
        when(passwordEncoder.matches(request.password(), parent.getPasswordHash())).thenReturn(true);
        when(jwtProvider.generateAccessToken(parent)).thenReturn("access-token");
        when(jwtProvider.getAccessTokenExpirySeconds()).thenReturn(1800L);

        TokenIssueResult result = authService.login(request);

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.accessTokenExpiresInSeconds()).isEqualTo(1800L);
        assertThat(result.rawRefreshToken()).isNotBlank();
        assertThat(result.refreshTokenMaxAgeSeconds()).isEqualTo(24 * 60 * 60L);
        verify(refreshTokenRepository).save(any());
    }

    @Test
    void login_issuesLongLivedRefreshTokenWhenAutoLoginChecked() {
        LoginRequest request = new LoginRequest("parent@example.com", "Abcdef1!", true);
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        when(parentRepository.findByEmail(request.email())).thenReturn(Optional.of(parent));
        when(passwordEncoder.matches(request.password(), parent.getPasswordHash())).thenReturn(true);
        when(jwtProvider.generateAccessToken(parent)).thenReturn("access-token");
        when(jwtProvider.getAccessTokenExpirySeconds()).thenReturn(1800L);

        TokenIssueResult result = authService.login(request);

        assertThat(result.refreshTokenMaxAgeSeconds()).isEqualTo(60 * 24 * 60 * 60L);
    }

    @Test
    void login_throwsWhenEmailNotFound() {
        LoginRequest request = new LoginRequest("missing@example.com", "Abcdef1!", false);
        when(parentRepository.findByEmail(request.email())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    void login_throwsWhenPasswordDoesNotMatch() {
        LoginRequest request = new LoginRequest("parent@example.com", "WrongPass1!", false);
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        when(parentRepository.findByEmail(request.email())).thenReturn(Optional.of(parent));
        when(passwordEncoder.matches(request.password(), parent.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    void login_throwsForGoogleOnlyAccountWithoutPassword() {
        LoginRequest request = new LoginRequest("google@example.com", "Abcdef1!", false);
        Parent parent = Parent.createGoogle("google@example.com", "google-sub-id", LocalDate.of(1990, 1, 1));
        when(parentRepository.findByEmail(request.email())).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    void login_throwsForDeletedAccount() {
        LoginRequest request = new LoginRequest("deleted@example.com", "Abcdef1!", false);
        Parent parent = Parent.createLocal("deleted@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(parent, "deletedAt", LocalDateTime.now());
        when(parentRepository.findByEmail(request.email())).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    void refresh_rotatesTokenAndPreservesRememberMe() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        RefreshToken oldToken = RefreshToken.issue(
                parent, TokenHasher.hash("raw-token"), LocalDateTime.now().plusDays(10), true);
        when(refreshTokenRepository.findByTokenHash(TokenHasher.hash("raw-token")))
                .thenReturn(Optional.of(oldToken));
        when(jwtProvider.generateAccessToken(parent)).thenReturn("new-access-token");
        when(jwtProvider.getAccessTokenExpirySeconds()).thenReturn(1800L);

        TokenIssueResult result = authService.refresh("raw-token");

        assertThat(oldToken.isRevoked()).isTrue();
        assertThat(result.accessToken()).isEqualTo("new-access-token");
        assertThat(result.refreshTokenMaxAgeSeconds()).isEqualTo(60 * 24 * 60 * 60L);
        verify(refreshTokenRepository).save(any());
    }

    @Test
    void refresh_throwsWhenCookieMissing() {
        assertThatThrownBy(() -> authService.refresh(null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));

        verify(refreshTokenRepository, never()).findByTokenHash(any());
    }

    @Test
    void refresh_throwsWhenTokenNotFound() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("unknown-token"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));
    }

    @Test
    void refresh_throwsWhenTokenExpired() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        RefreshToken expiredToken = RefreshToken.issue(
                parent, TokenHasher.hash("raw-token"), LocalDateTime.now().minusDays(1), false);
        when(refreshTokenRepository.findByTokenHash(TokenHasher.hash("raw-token")))
                .thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> authService.refresh("raw-token"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));
    }

    @Test
    void refresh_revokesAllTokensAndThrowsWhenReuseDetected() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        RefreshToken reusedToken = RefreshToken.issue(
                parent, TokenHasher.hash("raw-token"), LocalDateTime.now().plusDays(1), false);
        reusedToken.revoke();
        RefreshToken otherActiveToken = RefreshToken.issue(
                parent, TokenHasher.hash("other-token"), LocalDateTime.now().plusDays(1), false);
        when(refreshTokenRepository.findByTokenHash(TokenHasher.hash("raw-token")))
                .thenReturn(Optional.of(reusedToken));
        when(refreshTokenRepository.findAllByParentAndRevokedFalse(parent))
                .thenReturn(List.of(otherActiveToken));

        assertThatThrownBy(() -> authService.refresh("raw-token"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.REFRESH_TOKEN_REUSED));
        assertThat(otherActiveToken.isRevoked()).isTrue();
    }

    @Test
    void logout_revokesTokenMatchingCookieWhenOwnedByAuthenticatedParent() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(parent, "parentId", 1L);
        RefreshToken token = RefreshToken.issue(
                parent, TokenHasher.hash("raw-refresh-token"), LocalDateTime.now().plusDays(1), false);
        when(refreshTokenRepository.findByTokenHash(TokenHasher.hash("raw-refresh-token")))
                .thenReturn(Optional.of(token));

        authService.logout(1L, "raw-refresh-token");

        assertThat(token.isRevoked()).isTrue();
    }

    @Test
    void logout_doesNotRevokeTokenOwnedByDifferentParent() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(parent, "parentId", 1L);
        RefreshToken token = RefreshToken.issue(
                parent, TokenHasher.hash("raw-refresh-token"), LocalDateTime.now().plusDays(1), false);
        when(refreshTokenRepository.findByTokenHash(TokenHasher.hash("raw-refresh-token")))
                .thenReturn(Optional.of(token));

        authService.logout(2L, "raw-refresh-token");

        assertThat(token.isRevoked()).isFalse();
    }

    @Test
    void logout_doesNothingWhenCookieMissing() {
        authService.logout(1L, null);

        verify(refreshTokenRepository, never()).findByTokenHash(any());
    }

    @Test
    void logout_doesNothingWhenTokenNotFound() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        authService.logout(1L, "unknown-token");

        verify(refreshTokenRepository).findByTokenHash(TokenHasher.hash("unknown-token"));
    }

    @Test
    void signup_normalizesEmailCaseAndWhitespaceBeforeDuplicateCheck() {
        SignupRequest request = new SignupRequest(
                "  New@Example.com  ", "Abcdef1!", LocalDate.now().minusYears(20));
        when(parentRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(request.password())).thenReturn("hashed-password");
        when(parentRepository.save(any(Parent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Parent result = authService.signup(request);

        assertThat(result.getEmail()).isEqualTo("new@example.com");
    }

    @Test
    void signup_throwsWhenEmailAlreadyExistsWithDifferentCase() {
        SignupRequest request = new SignupRequest(
                "TAKEN@Example.com", "Abcdef1!", LocalDate.now().minusYears(20));
        Parent existing = Parent.createLocal("taken@example.com", "hash", LocalDate.of(1990, 1, 1));
        when(parentRepository.findByEmail("taken@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.EMAIL_ALREADY_EXISTS));
    }

    @Test
    void login_normalizesEmailCaseAndWhitespaceBeforeLookup() {
        LoginRequest request = new LoginRequest("  Parent@Example.com  ", "Abcdef1!", false);
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        when(parentRepository.findByEmail("parent@example.com")).thenReturn(Optional.of(parent));
        when(passwordEncoder.matches(request.password(), parent.getPasswordHash())).thenReturn(true);
        when(jwtProvider.generateAccessToken(parent)).thenReturn("access-token");
        when(jwtProvider.getAccessTokenExpirySeconds()).thenReturn(1800L);

        TokenIssueResult result = authService.login(request);

        assertThat(result.accessToken()).isEqualTo("access-token");
    }

    @Test
    void cleanupExpiredRefreshTokens_deletesTokensExpiredBeyondRetentionWindow() {
        when(refreshTokenRepository.deleteByExpiresAtBefore(any())).thenReturn(3L);

        authService.cleanupExpiredRefreshTokens();

        org.mockito.ArgumentCaptor<LocalDateTime> cutoffCaptor =
                org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        verify(refreshTokenRepository).deleteByExpiresAtBefore(cutoffCaptor.capture());
        assertThat(cutoffCaptor.getValue()).isBefore(LocalDateTime.now().minusDays(29));
    }

    @Test
    void googleAuth_returnsLoginSuccessWhenProviderIdAlreadyRegistered() {
        GoogleAuthRequest request = new GoogleAuthRequest("google-id-token");
        Parent parent = Parent.createGoogle("google@example.com", "google-sub-1", LocalDate.of(1990, 1, 1));
        when(googleTokenVerifier.verify("google-id-token"))
                .thenReturn(new GoogleUserInfo("google-sub-1", "google@example.com"));
        when(parentRepository.findByProviderId("google-sub-1")).thenReturn(Optional.of(parent));
        when(jwtProvider.generateAccessToken(parent)).thenReturn("access-token");
        when(jwtProvider.getAccessTokenExpirySeconds()).thenReturn(1800L);

        GoogleAuthResult result = authService.googleAuth(request);

        assertThat(result.outcome()).isEqualTo(GoogleAuthResult.GoogleAuthOutcome.LOGIN_SUCCESS);
        assertThat(result.tokenIssueResult().refreshTokenMaxAgeSeconds()).isEqualTo(60 * 24 * 60 * 60L);
        verify(refreshTokenRepository).save(any());
    }

    @Test
    void googleAuth_returnsLinkRequiredWhenEmailMatchesExistingLocalAccount() {
        GoogleAuthRequest request = new GoogleAuthRequest("google-id-token");
        Parent existingLocal = Parent.createLocal("shared@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(existingLocal, "parentId", 9L);
        when(googleTokenVerifier.verify("google-id-token"))
                .thenReturn(new GoogleUserInfo("google-sub-2", "shared@example.com"));
        when(parentRepository.findByProviderId("google-sub-2")).thenReturn(Optional.empty());
        when(parentRepository.findByEmail("shared@example.com")).thenReturn(Optional.of(existingLocal));
        when(jwtProvider.generateLinkToken(9L, "google-sub-2")).thenReturn("link-token");

        GoogleAuthResult result = authService.googleAuth(request);

        assertThat(result.outcome()).isEqualTo(GoogleAuthResult.GoogleAuthOutcome.LINK_REQUIRED);
        assertThat(result.linkToken()).isEqualTo("link-token");
    }

    @Test
    void googleAuth_returnsSignupRequiredWhenNoAccountMatches() {
        GoogleAuthRequest request = new GoogleAuthRequest("google-id-token");
        when(googleTokenVerifier.verify("google-id-token"))
                .thenReturn(new GoogleUserInfo("google-sub-3", "new-google@example.com"));
        when(parentRepository.findByProviderId("google-sub-3")).thenReturn(Optional.empty());
        when(parentRepository.findByEmail("new-google@example.com")).thenReturn(Optional.empty());
        when(jwtProvider.generateSignupToken("google-sub-3", "new-google@example.com")).thenReturn("signup-token");

        GoogleAuthResult result = authService.googleAuth(request);

        assertThat(result.outcome()).isEqualTo(GoogleAuthResult.GoogleAuthOutcome.SIGNUP_REQUIRED);
        assertThat(result.signupToken()).isEqualTo("signup-token");
        assertThat(result.email()).isEqualTo("new-google@example.com");
    }

    @Test
    void googleComplete_createsGoogleAccountWithUserProvidedBirthDate() {
        GoogleCompleteRequest request = new GoogleCompleteRequest("signup-token", LocalDate.now().minusYears(20));
        when(jwtProvider.parseSignupToken("signup-token"))
                .thenReturn(new JwtProvider.SignupTokenClaims("google-sub-4", "complete@example.com"));
        when(parentRepository.findByEmail("complete@example.com")).thenReturn(Optional.empty());
        when(parentRepository.save(any(Parent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Parent result = authService.googleComplete(request);

        assertThat(result.getEmail()).isEqualTo("complete@example.com");
        assertThat(result.getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(result.hasPassword()).isFalse();
    }

    @Test
    void googleComplete_throwsWhenUnderMinimumAge() {
        GoogleCompleteRequest request = new GoogleCompleteRequest("signup-token", LocalDate.now().minusYears(18));
        when(jwtProvider.parseSignupToken("signup-token"))
                .thenReturn(new JwtProvider.SignupTokenClaims("google-sub-5", "young@example.com"));

        assertThatThrownBy(() -> authService.googleComplete(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.UNDER_AGE));
    }

    @Test
    void googleComplete_throwsWhenEmailAlreadyRegisteredSinceTokenWasIssued() {
        GoogleCompleteRequest request = new GoogleCompleteRequest("signup-token", LocalDate.now().minusYears(20));
        when(jwtProvider.parseSignupToken("signup-token"))
                .thenReturn(new JwtProvider.SignupTokenClaims("google-sub-6", "taken@example.com"));
        Parent existing = Parent.createLocal("taken@example.com", "hash", LocalDate.of(1990, 1, 1));
        when(parentRepository.findByEmail("taken@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> authService.googleComplete(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.EMAIL_ALREADY_EXISTS));
    }

    @Test
    void googleComplete_propagatesInvalidSignupTokenError() {
        GoogleCompleteRequest request = new GoogleCompleteRequest("bad-token", LocalDate.now().minusYears(20));
        when(jwtProvider.parseSignupToken("bad-token"))
                .thenThrow(new BusinessException(AuthErrorCode.INVALID_SIGNUP_TOKEN));

        assertThatThrownBy(() -> authService.googleComplete(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_SIGNUP_TOKEN));
    }

    @Test
    void googleLink_linksAccountAndIssuesTokensWhenPasswordMatches() {
        GoogleLinkRequest request = new GoogleLinkRequest("link-token", "Abcdef1!");
        Parent parent = Parent.createLocal("shared@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(parent, "parentId", 11L);
        when(jwtProvider.parseLinkToken("link-token"))
                .thenReturn(new JwtProvider.LinkTokenClaims(11L, "google-sub-7"));
        when(parentRepository.findById(11L)).thenReturn(Optional.of(parent));
        when(passwordEncoder.matches("Abcdef1!", "hashed-password")).thenReturn(true);
        when(jwtProvider.generateAccessToken(parent)).thenReturn("access-token");
        when(jwtProvider.getAccessTokenExpirySeconds()).thenReturn(1800L);

        TokenIssueResult result = authService.googleLink(request);

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshTokenMaxAgeSeconds()).isEqualTo(60 * 24 * 60 * 60L);
        assertThat(parent.getProviderId()).isEqualTo("google-sub-7");
    }

    @Test
    void googleLink_throwsGenericInvalidCredentialsWhenPasswordDoesNotMatch() {
        GoogleLinkRequest request = new GoogleLinkRequest("link-token", "WrongPass1!");
        Parent parent = Parent.createLocal("shared@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(parent, "parentId", 11L);
        when(jwtProvider.parseLinkToken("link-token"))
                .thenReturn(new JwtProvider.LinkTokenClaims(11L, "google-sub-7"));
        when(parentRepository.findById(11L)).thenReturn(Optional.of(parent));
        when(passwordEncoder.matches("WrongPass1!", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.googleLink(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    void googleLink_throwsWhenAccountDeleted() {
        GoogleLinkRequest request = new GoogleLinkRequest("link-token", "Abcdef1!");
        Parent parent = Parent.createLocal("shared@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(parent, "parentId", 11L);
        ReflectionTestUtils.setField(parent, "deletedAt", LocalDateTime.now());
        when(jwtProvider.parseLinkToken("link-token"))
                .thenReturn(new JwtProvider.LinkTokenClaims(11L, "google-sub-7"));
        when(parentRepository.findById(11L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> authService.googleLink(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    void googleLink_propagatesInvalidLinkTokenError() {
        GoogleLinkRequest request = new GoogleLinkRequest("bad-token", "Abcdef1!");
        when(jwtProvider.parseLinkToken("bad-token"))
                .thenThrow(new BusinessException(AuthErrorCode.INVALID_LINK_TOKEN));

        assertThatThrownBy(() -> authService.googleLink(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_LINK_TOKEN));
    }

    @Test
    void changePassword_updatesHashAndRevokesOnlyOtherSessions() {
        Parent parent = Parent.createLocal("parent@example.com", "old-hash", LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(parent, "parentId", 1L);
        RefreshToken currentToken = RefreshToken.issue(
                parent, TokenHasher.hash("current-token"), LocalDateTime.now().plusDays(1), false);
        RefreshToken otherToken = RefreshToken.issue(
                parent, TokenHasher.hash("other-token"), LocalDateTime.now().plusDays(1), false);
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(passwordEncoder.matches("Abcdef1!", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("Newpass1!")).thenReturn("new-hash");
        when(refreshTokenRepository.findAllByParentAndRevokedFalse(parent))
                .thenReturn(List.of(currentToken, otherToken));

        authService.changePassword(1L, "Abcdef1!", "Newpass1!", "Newpass1!", "current-token");

        assertThat(parent.getPasswordHash()).isEqualTo("new-hash");
        assertThat(currentToken.isRevoked()).isFalse();
        assertThat(otherToken.isRevoked()).isTrue();
    }

    @Test
    void changePassword_revokesAllSessionsWhenCurrentRefreshTokenCookieMissing() {
        Parent parent = Parent.createLocal("parent@example.com", "old-hash", LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(parent, "parentId", 1L);
        RefreshToken someToken = RefreshToken.issue(
                parent, TokenHasher.hash("some-token"), LocalDateTime.now().plusDays(1), false);
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(passwordEncoder.matches("Abcdef1!", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("Newpass1!")).thenReturn("new-hash");
        when(refreshTokenRepository.findAllByParentAndRevokedFalse(parent))
                .thenReturn(List.of(someToken));

        authService.changePassword(1L, "Abcdef1!", "Newpass1!", "Newpass1!", null);

        assertThat(someToken.isRevoked()).isTrue();
    }

    @Test
    void changePassword_throwsWhenCurrentPasswordMismatch() {
        Parent parent = Parent.createLocal("parent@example.com", "old-hash", LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(parent, "parentId", 1L);
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(passwordEncoder.matches("wrong", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(1L, "wrong", "Newpass1!", "Newpass1!", "current-token"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.CURRENT_PASSWORD_MISMATCH));
        assertThat(parent.getPasswordHash()).isEqualTo("old-hash");
    }

    @Test
    void changePassword_throwsWhenNewPasswordConfirmationMismatch() {
        Parent parent = Parent.createLocal("parent@example.com", "old-hash", LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(parent, "parentId", 1L);
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(passwordEncoder.matches("Abcdef1!", "old-hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.changePassword(1L, "Abcdef1!", "Newpass1!", "Different1!", "current-token"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.NEW_PASSWORD_CONFIRMATION_MISMATCH));
        assertThat(parent.getPasswordHash()).isEqualTo("old-hash");
    }

    @Test
    void changePassword_throwsWhenPasswordNotSetOnGoogleOnlyAccount() {
        Parent parent = Parent.createGoogle("parent@example.com", "google-sub-1", LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(parent, "parentId", 1L);
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> authService.changePassword(1L, "any", "Newpass1!", "Newpass1!", "current-token"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.PASSWORD_NOT_SET));
    }

    @Test
    void changePassword_throwsWhenParentNotFound() {
        when(parentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.changePassword(99L, "Abcdef1!", "Newpass1!", "Newpass1!", "current-token"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.PARENT_NOT_FOUND));
    }

    @Test
    void changePassword_throwsWhenParentIsSoftDeleted() {
        Parent parent = Parent.createLocal("parent@example.com", "old-hash", LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(parent, "parentId", 1L);
        ReflectionTestUtils.setField(parent, "deletedAt", LocalDateTime.now());
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> authService.changePassword(1L, "Abcdef1!", "Newpass1!", "Newpass1!", "current-token"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.PARENT_NOT_FOUND));
    }

    @Test
    void withdraw_softDeletesParentVerifiesPinSoftDeletesChildrenAndRevokesAllTokens() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        parent.setPin("hashed-pin", LocalDateTime.now());
        ReflectionTestUtils.setField(parent, "parentId", 1L);
        RefreshToken token = RefreshToken.issue(
                parent, TokenHasher.hash("some-token"), LocalDateTime.now().plusDays(1), false);
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(refreshTokenRepository.findAllByParentAndRevokedFalse(parent)).thenReturn(List.of(token));

        authService.withdraw(1L, "1234");

        verify(pinService).verifyPin(1L, "1234");
        verify(childService).softDeleteChildrenOf(1L);
        assertThat(parent.isDeleted()).isTrue();
        assertThat(token.isRevoked()).isTrue();
    }

    @Test
    void withdraw_allowsWithoutPinWhenAccountHasNoPin() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(parent, "parentId", 1L);
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(refreshTokenRepository.findAllByParentAndRevokedFalse(parent)).thenReturn(List.of());

        authService.withdraw(1L, null);

        verify(pinService, never()).verifyPin(any(), any());
        verify(childService).softDeleteChildrenOf(1L);
        assertThat(parent.isDeleted()).isTrue();
    }

    @Test
    void withdraw_propagatesPinMismatchAndLeavesAccountActive() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        parent.setPin("hashed-pin", LocalDateTime.now());
        ReflectionTestUtils.setField(parent, "parentId", 1L);
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));
        doThrow(new BusinessException(PinErrorCode.PIN_MISMATCH)).when(pinService).verifyPin(1L, "0000");

        assertThatThrownBy(() -> authService.withdraw(1L, "0000"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PinErrorCode.PIN_MISMATCH));
        assertThat(parent.isDeleted()).isFalse();
        verify(childService, never()).softDeleteChildrenOf(any());
    }

    @Test
    void withdraw_throwsInvalidFormatWhenPinRequiredButMissing() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        parent.setPin("hashed-pin", LocalDateTime.now());
        ReflectionTestUtils.setField(parent, "parentId", 1L);
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> authService.withdraw(1L, "  "))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PinErrorCode.PIN_INVALID_FORMAT));
        // 형식 오류는 verifyPin을 타지 않으므로 잠금 카운트를 소모하지 않는다.
        verify(pinService, never()).verifyPin(any(), any());
        assertThat(parent.isDeleted()).isFalse();
    }

    @Test
    void withdraw_throwsInvalidFormatForMalformedPinWithoutConsumingLockoutAttempt() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        parent.setPin("hashed-pin", LocalDateTime.now());
        ReflectionTestUtils.setField(parent, "parentId", 1L);
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> authService.withdraw(1L, "123"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PinErrorCode.PIN_INVALID_FORMAT));
        verify(pinService, never()).verifyPin(any(), any());
        assertThat(parent.isDeleted()).isFalse();
    }

    @Test
    void withdraw_throwsWhenAccountAlreadyWithdrawnOrMissing() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(parent, "parentId", 1L);
        ReflectionTestUtils.setField(parent, "deletedAt", LocalDateTime.now());
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> authService.withdraw(1L, "1234"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.PARENT_NOT_FOUND));
        verify(pinService, never()).verifyPin(any(), any());
    }

    @Test
    void googleAuth_doesNotResurrectSoftDeletedAccountFoundByProviderId() {
        Parent deleted = Parent.createGoogle("parent@example.com", "google-sub-1", LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(deleted, "deletedAt", LocalDateTime.now());
        when(googleTokenVerifier.verify("id-token"))
                .thenReturn(new GoogleUserInfo("google-sub-1", "parent@example.com"));
        when(parentRepository.findByProviderId("google-sub-1")).thenReturn(Optional.of(deleted));
        when(parentRepository.findByEmail("parent@example.com")).thenReturn(Optional.of(deleted));
        when(jwtProvider.generateSignupToken("google-sub-1", "parent@example.com")).thenReturn("signup-token");

        GoogleAuthResult result = authService.googleAuth(new GoogleAuthRequest("id-token"));

        assertThat(result.outcome()).isEqualTo(GoogleAuthResult.GoogleAuthOutcome.SIGNUP_REQUIRED);
    }
}
