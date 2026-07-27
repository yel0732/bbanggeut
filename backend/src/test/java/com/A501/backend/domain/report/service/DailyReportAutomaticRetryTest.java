package com.A501.backend.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.A501.backend.domain.report.entity.DailyReport;
import com.A501.backend.domain.report.entity.ReportAttemptStatus;
import com.A501.backend.domain.report.entity.ReportErrorCode;
import com.A501.backend.domain.report.entity.ReportFailureStage;
import com.A501.backend.domain.report.entity.ReportGenerationStatus;
import com.A501.backend.domain.report.model.ReportGameSource;
import com.A501.backend.domain.report.model.ReportPeriod;
import com.A501.backend.domain.report.port.ConversationSummaryPort;
import com.A501.backend.domain.report.port.GameReportQueryPort;
import com.A501.backend.domain.report.repository.DailyReportRepository;
import com.A501.backend.domain.report.repository.ReportConversationSummaryRepository;
import com.A501.backend.domain.report.repository.ReportGenerationAttemptRepository;
import com.A501.backend.domain.report.repository.ReportGameResultRepository;

@SpringBootTest(properties = {
        "report.schedule.enabled=false",
        "report.retry.max-attempts=3",
        "report.retry.interval=PT0S"
})
class DailyReportAutomaticRetryTest {

    private static final Long CHILD_ID = 15L;
    private static final LocalDateTime STARTED_AT =
            LocalDateTime.of(2026, 7, 22, 19, 0);
    private static final LocalDateTime ENDED_AT =
            LocalDateTime.of(2026, 7, 23, 19, 0);

    @Autowired
    private DailyReportGenerationService generationService;

    @Autowired
    private DailyReportRepository dailyReportRepository;

    @Autowired
    private ReportGenerationAttemptRepository attemptRepository;

    @Autowired
    private ReportGameResultRepository gameResultRepository;

    @Autowired
    private ReportConversationSummaryRepository conversationSummaryRepository;

    @MockitoBean
    private GameReportQueryPort gameReportQueryPort;

    @MockitoBean
    private ConversationSummaryPort conversationSummaryPort;

    @MockitoSpyBean
    private ReportRetryWaiter retryWaiter;

    @BeforeEach
    void setUp() {
        conversationSummaryRepository.deleteAll();
        gameResultRepository.deleteAll();
        attemptRepository.deleteAll();
        dailyReportRepository.deleteAll();
    }

    @Test
    void 첫_시도_실패_후_자동_재시도에_성공한다() {
        DailyReport report = createPendingReport();
        when(gameReportQueryPort.findCompletedGames(
                CHILD_ID, STARTED_AT, ENDED_AT))
                .thenThrow(new IllegalStateException("temporary game failure"))
                .thenReturn(List.of(validGame()));
        when(conversationSummaryPort.generateSummary(
                CHILD_ID, STARTED_AT, ENDED_AT))
                .thenReturn(Optional.empty());

        generationService.generate(report.getId());

        DailyReport completed = reportOf(report);
        assertThat(completed.getGenerationStatus())
                .isEqualTo(ReportGenerationStatus.COMPLETED);
        assertThat(completed.getRetryCount()).isEqualTo(1);
        assertThat(gameResultRepository.findAllByReportId(report.getId()))
                .hasSize(1);

        var attempts = attemptRepository
                .findAllByReportIdOrderByAttemptNumberAsc(report.getId());
        assertThat(attempts).hasSize(2);
        assertThat(attempts.get(0).getAttemptStatus())
                .isEqualTo(ReportAttemptStatus.FAILED);
        assertThat(attempts.get(0).getFailureStage())
                .isEqualTo(ReportFailureStage.GAME_QUERY);
        assertThat(attempts.get(0).getErrorCode())
                .isEqualTo(ReportErrorCode.GAME_QUERY_FAILED);
        assertThat(attempts.get(1).getAttemptStatus())
                .isEqualTo(ReportAttemptStatus.COMPLETED);
        verify(retryWaiter, times(1)).waitForNextAttempt();
    }

    @Test
    void 모든_자동_재시도가_실패하면_최종_FAILED로_변경한다() {
        DailyReport report = createPendingReport();
        when(gameReportQueryPort.findCompletedGames(
                CHILD_ID, STARTED_AT, ENDED_AT))
                .thenThrow(new IllegalStateException("game service unavailable"));

        assertThatThrownBy(() -> generationService.generate(report.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("game service unavailable");

        DailyReport failed = reportOf(report);
        assertThat(failed.getGenerationStatus())
                .isEqualTo(ReportGenerationStatus.FAILED);
        assertThat(failed.getRetryCount()).isEqualTo(2);
        assertThat(failed.getFailureReason())
                .isEqualTo("game service unavailable");
        assertThat(gameResultRepository.findAll()).isEmpty();
        assertThat(conversationSummaryRepository.findAll()).isEmpty();

        var attempts = attemptRepository
                .findAllByReportIdOrderByAttemptNumberAsc(report.getId());
        assertThat(attempts).hasSize(3)
                .allMatch(attempt ->
                        attempt.getAttemptStatus() == ReportAttemptStatus.FAILED)
                .allMatch(attempt ->
                        attempt.getFailureStage() == ReportFailureStage.GAME_QUERY)
                .allMatch(attempt ->
                        attempt.getErrorCode() == ReportErrorCode.GAME_QUERY_FAILED);
        verify(retryWaiter, times(2)).waitForNextAttempt();
    }

    @Test
    void 원본_데이터가_없으면_재시도_없이_정상_완료한다() {
        DailyReport report = createPendingReport();
        when(gameReportQueryPort.findCompletedGames(
                CHILD_ID, STARTED_AT, ENDED_AT))
                .thenReturn(List.of());
        when(conversationSummaryPort.generateSummary(
                CHILD_ID, STARTED_AT, ENDED_AT))
                .thenReturn(Optional.empty());

        var result = generationService.generate(report.getId());

        assertThat(result.hasActivity()).isFalse();
        assertThat(reportOf(report).getGenerationStatus())
                .isEqualTo(ReportGenerationStatus.COMPLETED);
        assertThat(reportOf(report).getRetryCount()).isZero();
        assertThat(attemptRepository
                .findAllByReportIdOrderByAttemptNumberAsc(report.getId()))
                .singleElement()
                .satisfies(attempt ->
                        assertThat(attempt.getAttemptStatus())
                                .isEqualTo(ReportAttemptStatus.COMPLETED));
        verify(retryWaiter, times(0)).waitForNextAttempt();
    }

    private DailyReport createPendingReport() {
        return dailyReportRepository.saveAndFlush(
                DailyReport.pending(
                        CHILD_ID,
                        new ReportPeriod(
                                STARTED_AT,
                                ENDED_AT,
                                LocalDate.of(2026, 7, 23))));
    }

    private DailyReport reportOf(DailyReport report) {
        return dailyReportRepository.findById(report.getId()).orElseThrow();
    }

    private ReportGameSource validGame() {
        return new ReportGameSource(
                UUID.randomUUID(),
                1L,
                "DOBBLE",
                "도블",
                LocalDateTime.of(2026, 7, 23, 10, 0),
                180,
                3500,
                null);
    }
}
