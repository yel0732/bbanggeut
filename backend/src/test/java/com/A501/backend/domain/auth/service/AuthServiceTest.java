package com.A501.backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.A501.backend.domain.auth.dto.request.LoginRequest;
import com.A501.backend.domain.auth.dto.request.SignupRequest;
import com.A501.backend.domain.auth.entity.Parent;
import com.A501.backend.domain.auth.entity.RefreshToken;
import com.A501.backend.domain.auth.exception.AuthErrorCode;
import com.A501.backend.domain.auth.repository.ParentRepository;
import com.A501.backend.domain.auth.repository.RefreshTokenRepository;
import com.A501.backend.domain.auth.security.JwtProvider;
import com.A501.backend.domain.auth.security.RefreshTokenProperties;
import com.A501.backend.domain.auth.security.TokenHasher;

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

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                parentRepository, refreshTokenRepository, passwordEncoder, jwtProvider, REFRESH_TOKEN_PROPERTIES);
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
    void logout_revokesTokenMatchingCookie() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        RefreshToken token = RefreshToken.issue(
                parent, TokenHasher.hash("raw-refresh-token"), LocalDateTime.now().plusDays(1), false);
        when(refreshTokenRepository.findByTokenHash(TokenHasher.hash("raw-refresh-token")))
                .thenReturn(Optional.of(token));

        authService.logout("raw-refresh-token");

        assertThat(token.isRevoked()).isTrue();
    }

    @Test
    void logout_doesNothingWhenCookieMissing() {
        authService.logout(null);

        verify(refreshTokenRepository, never()).findByTokenHash(any());
    }

    @Test
    void logout_doesNothingWhenTokenNotFound() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        authService.logout("unknown-token");

        verify(refreshTokenRepository).findByTokenHash(TokenHasher.hash("unknown-token"));
    }
}
