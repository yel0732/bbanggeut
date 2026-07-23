package com.A501.backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.auth.dto.request.SignupRequest;
import com.A501.backend.domain.auth.entity.Parent;
import com.A501.backend.domain.auth.repository.ParentRepository;

@SpringBootTest
@Transactional
class AuthServiceIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private ParentRepository parentRepository;

    @Test
    void signup_persistsBirthDateAndAuditingTimestampsInRealAppContext() {
        SignupRequest request = new SignupRequest(
                "integration@example.com", "Abcdef1!", LocalDate.of(2000, 3, 15));

        authService.signup(request);

        Parent saved = parentRepository.findByEmail("integration@example.com").orElseThrow();
        assertThat(saved.getBirthDate()).isEqualTo(LocalDate.of(2000, 3, 15));
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}
