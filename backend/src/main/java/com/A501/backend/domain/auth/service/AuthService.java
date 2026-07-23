package com.A501.backend.domain.auth.service;

import java.time.LocalDate;
import java.time.Period;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.auth.dto.request.SignupRequest;
import com.A501.backend.domain.auth.entity.Parent;
import com.A501.backend.domain.auth.exception.AuthErrorCode;
import com.A501.backend.domain.auth.repository.ParentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final int MINIMUM_SIGNUP_AGE = 19;

    private final ParentRepository parentRepository;
    private final PasswordEncoder passwordEncoder;

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
}
