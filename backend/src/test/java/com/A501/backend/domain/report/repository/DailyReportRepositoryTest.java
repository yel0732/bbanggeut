package com.A501.backend.domain.report.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import com.A501.backend.config.JpaAuditingConfig;
import com.A501.backend.domain.report.entity.DailyReport;
import com.A501.backend.domain.report.entity.ReportGenerationStatus;
import com.A501.backend.domain.report.model.ReportPeriod;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class DailyReportRepositoryTest {

    @Autowired
    private DailyReportRepository dailyReportRepository;

    @Test
    void PENDING_리포트를_저장한다() {
        ReportPeriod period = createPeriod();

        DailyReport saved = dailyReportRepository.saveAndFlush(
                DailyReport.pending(1L, period));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getChildId()).isEqualTo(1L);
        assertThat(saved.getReportDate()).isEqualTo(period.reportDate());
        assertThat(saved.getGenerationStatus())
                .isEqualTo(ReportGenerationStatus.PENDING);
        assertThat(saved.getRetryCount()).isZero();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void 같은_아이와_날짜의_리포트는_중복_저장할_수_없다() {
        ReportPeriod period = createPeriod();
        dailyReportRepository.saveAndFlush(DailyReport.pending(1L, period));

        assertThatThrownBy(() -> dailyReportRepository.saveAndFlush(
                DailyReport.pending(1L, period)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 다른_아이는_같은_날짜의_리포트를_저장할_수_있다() {
        ReportPeriod period = createPeriod();

        dailyReportRepository.saveAndFlush(DailyReport.pending(1L, period));
        dailyReportRepository.saveAndFlush(DailyReport.pending(2L, period));

        assertThat(dailyReportRepository.count()).isEqualTo(2);
    }

    @Test
    void 같은_아이도_다른_날짜의_리포트를_저장할_수_있다() {
        ReportPeriod firstPeriod = createPeriod();
        ReportPeriod nextPeriod = new ReportPeriod(
                firstPeriod.startedAt().plusDays(1),
                firstPeriod.endedAt().plusDays(1),
                firstPeriod.reportDate().plusDays(1));

        dailyReportRepository.saveAndFlush(DailyReport.pending(1L, firstPeriod));
        dailyReportRepository.saveAndFlush(DailyReport.pending(1L, nextPeriod));

        assertThat(dailyReportRepository.count()).isEqualTo(2);
    }

    private ReportPeriod createPeriod() {
        return new ReportPeriod(
                LocalDateTime.of(2026, 7, 22, 19, 0),
                LocalDateTime.of(2026, 7, 23, 19, 0),
                LocalDate.of(2026, 7, 23));
    }
}
