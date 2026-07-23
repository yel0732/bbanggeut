package com.A501.backend.domain.report.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.A501.backend.domain.report.model.ReportPeriod;

class DailyReportStateTest {

    private static final LocalDateTime PROCESSING_AT =
            LocalDateTime.of(2026, 7, 23, 10, 0);

    @Test
    void 신규_리포트는_PENDING으로_생성된다() {
        DailyReport report = createPendingReport();

        assertThat(report.getGenerationStatus())
                .isEqualTo(ReportGenerationStatus.PENDING);
        assertThat(report.getRetryCount()).isZero();
        assertThat(report.getProcessingStartedAt()).isNull();
        assertThat(report.getCompletedAt()).isNull();
        assertThat(report.getFailedAt()).isNull();
        assertThat(report.getFailureReason()).isNull();
    }

    @Test
    void PROCESSING에서_COMPLETED로_전환하며_시각을_기록한다() {
        DailyReport report = createPendingReport();
        LocalDateTime completedAt = PROCESSING_AT.plusMinutes(1);

        report.startProcessing(PROCESSING_AT);
        report.complete(completedAt);

        assertThat(report.getGenerationStatus())
                .isEqualTo(ReportGenerationStatus.COMPLETED);
        assertThat(report.getProcessingStartedAt()).isEqualTo(PROCESSING_AT);
        assertThat(report.getCompletedAt()).isEqualTo(completedAt);
        assertThat(report.getFailedAt()).isNull();
        assertThat(report.getFailureReason()).isNull();
    }

    @Test
    void PROCESSING에서_FAILED로_전환하며_원인과_시각을_기록한다() {
        DailyReport report = createPendingReport();
        LocalDateTime failedAt = PROCESSING_AT.plusSeconds(30);

        report.startProcessing(PROCESSING_AT);
        report.fail(failedAt, "AI summary timeout");

        assertThat(report.getGenerationStatus())
                .isEqualTo(ReportGenerationStatus.FAILED);
        assertThat(report.getFailedAt()).isEqualTo(failedAt);
        assertThat(report.getFailureReason()).isEqualTo("AI summary timeout");
        assertThat(report.getCompletedAt()).isNull();
    }

    @Test
    void FAILED_리포트를_재시도하면_실패_정보를_지우고_횟수를_증가시킨다() {
        DailyReport report = createPendingReport();
        report.startProcessing(PROCESSING_AT);
        report.fail(PROCESSING_AT.plusSeconds(30), "temporary failure");

        LocalDateTime retriedAt = PROCESSING_AT.plusMinutes(5);
        report.startProcessing(retriedAt);

        assertThat(report.getGenerationStatus())
                .isEqualTo(ReportGenerationStatus.PROCESSING);
        assertThat(report.getRetryCount()).isEqualTo(1);
        assertThat(report.getProcessingStartedAt()).isEqualTo(retriedAt);
        assertThat(report.getFailedAt()).isNull();
        assertThat(report.getFailureReason()).isNull();
    }

    @Test
    void PROCESSING_중_자동_재시도를_기록한다() {
        DailyReport report = createPendingReport();
        report.startProcessing(PROCESSING_AT);

        report.recordRetry();
        report.recordRetry();

        assertThat(report.getRetryCount()).isEqualTo(2);
        assertThat(report.getGenerationStatus())
                .isEqualTo(ReportGenerationStatus.PROCESSING);
    }

    @Test
    void 허용되지_않은_상태_전환을_거부한다() {
        DailyReport pending = createPendingReport();
        assertThatThrownBy(() ->
                pending.complete(PROCESSING_AT))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() ->
                pending.fail(PROCESSING_AT, "failure"))
                .isInstanceOf(IllegalStateException.class);

        DailyReport processing = createPendingReport();
        processing.startProcessing(PROCESSING_AT);
        assertThatThrownBy(() ->
                processing.startProcessing(PROCESSING_AT.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);

        DailyReport completed = createPendingReport();
        completed.startProcessing(PROCESSING_AT);
        completed.complete(PROCESSING_AT.plusSeconds(1));
        assertThatThrownBy(() ->
                completed.startProcessing(PROCESSING_AT.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() ->
                completed.fail(PROCESSING_AT.plusSeconds(2), "failure"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 상태_시각과_실패_원인은_필수다() {
        DailyReport report = createPendingReport();

        assertThatThrownBy(() -> report.startProcessing(null))
                .isInstanceOf(IllegalArgumentException.class);

        report.startProcessing(PROCESSING_AT);
        assertThatThrownBy(() -> report.complete(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> report.fail(null, "failure"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> report.fail(PROCESSING_AT, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private DailyReport createPendingReport() {
        return DailyReport.pending(
                15L,
                new ReportPeriod(
                        LocalDateTime.of(2026, 7, 22, 19, 0),
                        LocalDateTime.of(2026, 7, 23, 19, 0),
                        LocalDate.of(2026, 7, 23)));
    }
}
