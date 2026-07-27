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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.A501.backend.domain.report.entity.DailyReport;
import com.A501.backend.domain.report.entity.ReportGenerationStatus;
import com.A501.backend.domain.report.model.ReportConversationSummarySource;
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
        "report.retry.max-attempts=1",
        "report.retry.interval=PT0S"
})
class DailyReportGenerationServiceTest {

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
    private ReportGameResultRepository gameResultRepository;

    @Autowired
    private ReportConversationSummaryRepository conversationSummaryRepository;

    @Autowired
    private ReportGenerationAttemptRepository attemptRepository;

    @MockitoBean
    private GameReportQueryPort gameReportQueryPort;

    @MockitoBean
    private ConversationSummaryPort conversationSummaryPort;

    @BeforeEach
    void setUp() {
        conversationSummaryRepository.deleteAll();
        gameResultRepository.deleteAll();
        attemptRepository.deleteAll();
        dailyReportRepository.deleteAll();
    }

    @Test
    void completesReportWithGameAndConversationData() {
        DailyReport report = createPendingReport();
        UUID gameSessionId = UUID.randomUUID();
        when(gameReportQueryPort.findCompletedGames(
                CHILD_ID, STARTED_AT, ENDED_AT))
                .thenReturn(List.of(validGame(gameSessionId)));
        when(conversationSummaryPort.generateSummary(
                CHILD_ID, STARTED_AT, ENDED_AT))
                .thenReturn(Optional.of(validSummary()));

        var result = generationService.generate(report.getId());

        assertThat(result.gameResultCount()).isEqualTo(1);
        assertThat(result.conversationSummaryIncluded()).isTrue();
        assertThat(reportOf(report).getGenerationStatus())
                .isEqualTo(ReportGenerationStatus.COMPLETED);
        assertThat(reportOf(report).getProcessingStartedAt()).isNotNull();
        assertThat(reportOf(report).getCompletedAt()).isNotNull();
        assertThat(reportOf(report).getFailedAt()).isNull();
        assertThat(reportOf(report).getFailureReason()).isNull();
        assertThat(gameResultRepository.findAllByReportId(report.getId()))
                .singleElement()
                .satisfies(snapshot ->
                        assertThat(snapshot.getGameSessionId())
                                .isEqualTo(gameSessionId));
        assertThat(conversationSummaryRepository.findByReportId(report.getId()))
                .get()
                .satisfies(summary -> {
                    assertThat(summary.getDiaryTitle()).isEqualTo("오늘의 모험");
                    assertThat(summary.getSourceConversationIds())
                            .contains("conversation-1");
                    assertThat(summary.getMainEvents()).contains("친구와 놀이");
                });
    }

    @Test
    void completesReportWithOnlyGameData() {
        DailyReport report = createPendingReport();
        when(gameReportQueryPort.findCompletedGames(
                CHILD_ID, STARTED_AT, ENDED_AT))
                .thenReturn(List.of(validGame(UUID.randomUUID())));
        when(conversationSummaryPort.generateSummary(
                CHILD_ID, STARTED_AT, ENDED_AT))
                .thenReturn(Optional.empty());

        var result = generationService.generate(report.getId());

        assertThat(result.gameResultCount()).isEqualTo(1);
        assertThat(result.conversationSummaryIncluded()).isFalse();
        assertThat(statusOf(report)).isEqualTo(ReportGenerationStatus.COMPLETED);
        assertThat(conversationSummaryRepository.findAll()).isEmpty();
    }

    @Test
    void completesReportWithOnlyConversationData() {
        DailyReport report = createPendingReport();
        when(gameReportQueryPort.findCompletedGames(
                CHILD_ID, STARTED_AT, ENDED_AT))
                .thenReturn(List.of());
        when(conversationSummaryPort.generateSummary(
                CHILD_ID, STARTED_AT, ENDED_AT))
                .thenReturn(Optional.of(validSummary()));

        var result = generationService.generate(report.getId());

        assertThat(result.gameResultCount()).isZero();
        assertThat(result.conversationSummaryIncluded()).isTrue();
        assertThat(statusOf(report)).isEqualTo(ReportGenerationStatus.COMPLETED);
        assertThat(gameResultRepository.findAll()).isEmpty();
    }

    @Test
    void completesReportWithoutSnapshotsWhenBothAreasAreEmpty() {
        DailyReport report = createPendingReport();
        when(gameReportQueryPort.findCompletedGames(
                CHILD_ID, STARTED_AT, ENDED_AT))
                .thenReturn(List.of());
        when(conversationSummaryPort.generateSummary(
                CHILD_ID, STARTED_AT, ENDED_AT))
                .thenReturn(Optional.empty());

        var result = generationService.generate(report.getId());

        assertThat(result.hasActivity()).isFalse();
        DailyReport completedReport = reportOf(report);
        assertThat(completedReport.getGenerationStatus())
                .isEqualTo(ReportGenerationStatus.COMPLETED);
        assertThat(completedReport.getCompletedAt()).isNotNull();
        assertThat(completedReport.getFailedAt()).isNull();
        assertThat(completedReport.getFailureReason()).isNull();
        assertThat(gameResultRepository.findAll()).isEmpty();
        assertThat(conversationSummaryRepository.findAll()).isEmpty();
    }

    @Test
    void sanitizesAndLimitsFailureReason() {
        DailyReport report = createPendingReport();
        String longMessage = "AI failure\n\t" + "x".repeat(1200);
        when(gameReportQueryPort.findCompletedGames(
                CHILD_ID, STARTED_AT, ENDED_AT))
                .thenThrow(new IllegalStateException(longMessage));

        assertThatThrownBy(() -> generationService.generate(report.getId()))
                .isInstanceOf(IllegalStateException.class);

        DailyReport failedReport = reportOf(report);
        assertThat(failedReport.getGenerationStatus())
                .isEqualTo(ReportGenerationStatus.FAILED);
        assertThat(failedReport.getFailureReason())
                .hasSize(1000)
                .doesNotContain("\n", "\r", "\t");
    }

    @Test
    void retriesFailedReportAndClearsFailureInformationOnSuccess() {
        DailyReport report = createPendingReport();
        when(gameReportQueryPort.findCompletedGames(
                CHILD_ID, STARTED_AT, ENDED_AT))
                .thenThrow(new IllegalStateException("temporary game failure"))
                .thenReturn(List.of(validGame(UUID.randomUUID())));
        when(conversationSummaryPort.generateSummary(
                CHILD_ID, STARTED_AT, ENDED_AT))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> generationService.generate(report.getId()))
                .isInstanceOf(IllegalStateException.class);

        generationService.generate(report.getId());

        DailyReport completedReport = reportOf(report);
        assertThat(completedReport.getGenerationStatus())
                .isEqualTo(ReportGenerationStatus.COMPLETED);
        assertThat(completedReport.getRetryCount()).isEqualTo(1);
        assertThat(completedReport.getCompletedAt()).isNotNull();
        assertThat(completedReport.getFailedAt()).isNull();
        assertThat(completedReport.getFailureReason()).isNull();
    }

    @Test
    void allowsOnlyOneConcurrentGenerationForTheSameReport() throws Exception {
        DailyReport report = createPendingReport();
        CountDownLatch generationStarted = new CountDownLatch(1);
        CountDownLatch allowGenerationToFinish = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        when(gameReportQueryPort.findCompletedGames(
                CHILD_ID, STARTED_AT, ENDED_AT))
                .thenAnswer(invocation -> {
                    generationStarted.countDown();
                    if (!allowGenerationToFinish.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Test generation timed out.");
                    }
                    return List.of(validGame(UUID.randomUUID()));
                });
        when(conversationSummaryPort.generateSummary(
                CHILD_ID, STARTED_AT, ENDED_AT))
                .thenReturn(Optional.empty());

        try {
            Future<?> first = executorService.submit(() ->
                    generationService.generate(report.getId()));
            assertThat(generationStarted.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> duplicate = executorService.submit(() ->
                    generationService.generate(report.getId()));

            assertThatThrownBy(() -> duplicate.get(5, TimeUnit.SECONDS))
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage("The report is already being processed.");

            allowGenerationToFinish.countDown();
            first.get(5, TimeUnit.SECONDS);
        } finally {
            allowGenerationToFinish.countDown();
            executorService.shutdownNow();
        }

        verify(gameReportQueryPort, times(1)).findCompletedGames(
                CHILD_ID, STARTED_AT, ENDED_AT);
        verify(conversationSummaryPort, times(1)).generateSummary(
                CHILD_ID, STARTED_AT, ENDED_AT);
        assertThat(reportOf(report).getGenerationStatus())
                .isEqualTo(ReportGenerationStatus.COMPLETED);
        assertThat(gameResultRepository.findAllByReportId(report.getId()))
                .hasSize(1);
    }

    @Test
    void rollsBackGameSnapshotWhenConversationSnapshotFails() {
        DailyReport report = createPendingReport();
        when(gameReportQueryPort.findCompletedGames(
                CHILD_ID, STARTED_AT, ENDED_AT))
                .thenReturn(List.of(validGame(UUID.randomUUID())));
        when(conversationSummaryPort.generateSummary(
                CHILD_ID, STARTED_AT, ENDED_AT))
                .thenReturn(Optional.of(invalidSummary()));

        assertThatThrownBy(() ->
                generationService.generate(report.getId()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(statusOf(report)).isEqualTo(ReportGenerationStatus.FAILED);
        assertThat(gameResultRepository.findAll()).isEmpty();
        assertThat(conversationSummaryRepository.findAll()).isEmpty();
    }

    @Test
    void doesNotSaveConversationSnapshotWhenGameSnapshotFails() {
        DailyReport report = createPendingReport();
        when(gameReportQueryPort.findCompletedGames(
                CHILD_ID, STARTED_AT, ENDED_AT))
                .thenReturn(List.of(invalidGame()));
        when(conversationSummaryPort.generateSummary(
                CHILD_ID, STARTED_AT, ENDED_AT))
                .thenReturn(Optional.of(validSummary()));

        assertThatThrownBy(() ->
                generationService.generate(report.getId()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(statusOf(report)).isEqualTo(ReportGenerationStatus.FAILED);
        assertThat(gameResultRepository.findAll()).isEmpty();
        assertThat(conversationSummaryRepository.findAll()).isEmpty();
    }

    private DailyReport createPendingReport() {
        ReportPeriod period = new ReportPeriod(
                STARTED_AT,
                ENDED_AT,
                LocalDate.of(2026, 7, 23));
        return dailyReportRepository.saveAndFlush(
                DailyReport.pending(CHILD_ID, period));
    }

    private ReportGenerationStatus statusOf(DailyReport report) {
        return reportOf(report).getGenerationStatus();
    }

    private DailyReport reportOf(DailyReport report) {
        return dailyReportRepository.findById(report.getId()).orElseThrow();
    }

    private ReportGameSource validGame(UUID gameSessionId) {
        return new ReportGameSource(
                gameSessionId,
                1L,
                "DOBBLE",
                "도블",
                LocalDateTime.of(2026, 7, 23, 10, 0),
                180,
                3500,
                null);
    }

    private ReportGameSource invalidGame() {
        return new ReportGameSource(
                UUID.randomUUID(),
                1L,
                "DOBBLE",
                "",
                LocalDateTime.of(2026, 7, 23, 10, 0),
                180,
                3500,
                null);
    }

    private ReportConversationSummarySource validSummary() {
        return new ReportConversationSummarySource(
                List.of("conversation-1"),
                "오늘의 모험",
                "오늘 친구와 즐겁게 놀았다고 이야기했습니다.",
                List.of("친구와 놀이"),
                List.of("즐거움"),
                List.of("카드 게임"),
                "model-v1",
                "prompt-v1");
    }

    private ReportConversationSummarySource invalidSummary() {
        return new ReportConversationSummarySource(
                List.of("conversation-1"),
                "",
                "본문",
                List.of(),
                List.of(),
                List.of(),
                "model-v1",
                "prompt-v1");
    }
}
