package com.A501.backend.domain.report.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class ReportGenerationAttemptTest {

    private static final LocalDateTime STARTED_AT =
            LocalDateTime.of(2026, 7, 23, 10, 0);

    @Test
    void 생성_시도는_PROCESSING으로_시작하고_COMPLETED로_끝난다() {
        ReportGenerationAttempt attempt =
                ReportGenerationAttempt.processing(1L, 1, STARTED_AT);

        attempt.complete(STARTED_AT.plusSeconds(1));

        assertThat(attempt.getAttemptStatus())
                .isEqualTo(ReportAttemptStatus.COMPLETED);
        assertThat(attempt.getEndedAt()).isEqualTo(STARTED_AT.plusSeconds(1));
        assertThat(attempt.getFailureStage()).isNull();
        assertThat(attempt.getErrorCode()).isNull();
        assertThat(attempt.getFailureReason()).isNull();
    }

    @Test
    void 실패한_시도는_단계와_오류_코드와_원인을_기록한다() {
        ReportGenerationAttempt attempt =
                ReportGenerationAttempt.processing(1L, 2, STARTED_AT);

        attempt.fail(
                STARTED_AT.plusSeconds(1),
                ReportFailureStage.GAME_QUERY,
                ReportErrorCode.GAME_QUERY_FAILED,
                "game timeout");

        assertThat(attempt.getAttemptStatus())
                .isEqualTo(ReportAttemptStatus.FAILED);
        assertThat(attempt.getFailureStage())
                .isEqualTo(ReportFailureStage.GAME_QUERY);
        assertThat(attempt.getErrorCode())
                .isEqualTo(ReportErrorCode.GAME_QUERY_FAILED);
        assertThat(attempt.getFailureReason()).isEqualTo("game timeout");
    }

    @Test
    void 완료된_시도는_다시_완료하거나_실패할_수_없다() {
        ReportGenerationAttempt attempt =
                ReportGenerationAttempt.processing(1L, 1, STARTED_AT);
        attempt.complete(STARTED_AT.plusSeconds(1));

        assertThatThrownBy(() ->
                attempt.complete(STARTED_AT.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() ->
                attempt.fail(
                        STARTED_AT.plusSeconds(2),
                        ReportFailureStage.UNKNOWN,
                        ReportErrorCode.UNKNOWN_GENERATION_FAILURE,
                        "failure"))
                .isInstanceOf(IllegalStateException.class);
    }
}
