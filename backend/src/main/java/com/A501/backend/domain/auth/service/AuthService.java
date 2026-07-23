package com.A501.backend.domain.auth.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.auth.dto.request.SignupRequest;
import com.A501.backend.domain.auth.entity.Parent;
import com.A501.backend.domain.auth.entity.RefreshToken;
import com.A501.backend.domain.auth.exception.AuthErrorCode;
import com.A501.backend.domain.auth.repository.ParentRepository;
import com.A501.backend.domain.auth.repository.RefreshTokenRepository;
import com.A501.backend.domain.auth.security.JwtProvider;
import com.A501.backend.domain.auth.security.TokenHasher;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final int MINIMUM_SIGNUP_AGE = 19;
    private static final long REMEMBER_ME_REFRESH_TOKEN_EXPIRY_DAYS = 60;

    private final ParentRepository parentRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public boolean isEmailAvailable(String email) {
        return parentRepository.findByEmail(email).isEmpty();
    }

    @Transactional
    public Parent signup(SignupRequest request) {
        if (parentRepository.findByEmail(request.email()).isPresent()) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (isUnderAge(request.birthDate())) {
            throw new BusinessException(AuthErrorCode.UNDER_AGE);
        }

        Parent parent = Parent.createLocal(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.birthDate());
        return parentRepository.save(parent);
    }

    private boolean isUnderAge(LocalDate birthDate) {
        return Period.between(birthDate, LocalDate.now()).getYears() < MINIMUM_SIGNUP_AGE;
    }

    private TokenIssueResult issueTokens(Parent parent, long refreshTokenExpiryDays) {
        String accessToken = jwtProvider.generateAccessToken(parent);
        boolean rememberMe = refreshTokenExpiryDays == REMEMBER_ME_REFRESH_TOKEN_EXPIRY_DAYS;

        String rawRefreshToken = TokenHasher.generateRawToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(refreshTokenExpiryDays);
        refreshTokenRepository.save(
                RefreshToken.issue(parent, TokenHasher.hash(rawRefreshToken), expiresAt, rememberMe));

        long maxAgeSeconds = refreshTokenExpiryDays * 24 * 60 * 60;
        return new TokenIssueResult(
                parent, accessToken, jwtProvider.getAccessTokenExpirySeconds(), rawRefreshToken, maxAgeSeconds);
    }
}
