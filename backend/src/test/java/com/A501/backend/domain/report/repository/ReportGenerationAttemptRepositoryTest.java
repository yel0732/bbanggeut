package com.A501.backend.domain.report.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import com.A501.backend.config.JpaAuditingConfig;
import com.A501.backend.domain.report.entity.ReportGenerationAttempt;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class ReportGenerationAttemptRepositoryTest {

    @Autowired
    private ReportGenerationAttemptRepository attemptRepository;

    @Test
    void 같은_리포트와_시도_번호는_중복_저장할_수_없다() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 7, 23, 10, 0);
        attemptRepository.saveAndFlush(
                ReportGenerationAttempt.processing(1L, 1, startedAt));

        assertThatThrownBy(() ->
                attemptRepository.saveAndFlush(
                        ReportGenerationAttempt.processing(
                                1L,
                                1,
                                startedAt.plusSeconds(1))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
