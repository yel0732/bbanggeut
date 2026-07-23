package com.A501.backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.auth.dto.request.SignupRequest;
import com.A501.backend.domain.auth.entity.Parent;
import com.A501.backend.domain.auth.exception.AuthErrorCode;
import com.A501.backend.domain.auth.repository.ParentRepository;
import com.A501.backend.domain.auth.repository.RefreshTokenRepository;
import com.A501.backend.domain.auth.security.JwtProvider;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

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
        authService = new AuthService(parentRepository, refreshTokenRepository, passwordEncoder, jwtProvider);
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
    void signup_createsAccountWithoutIssuingTokens() {
        SignupRequest request = new SignupRequest(
                "new@example.com", "Abcdef1!", LocalDate.now().minusYears(20));
        when(parentRepository.findByEmail(request.email())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(request.password())).thenReturn("hashed-password");
        when(parentRepository.save(any(Parent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Parent result = authService.signup(request);

        verify(parentRepository).save(any(Parent.class));
        // 가입 후 로그인 화면으로 유도하는 흐름이라 회원가입 자체는 토큰을 발급하지 않는다.
        verify(jwtProvider, never()).generateAccessToken(any());
        verify(refreshTokenRepository, never()).save(any());
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
}
