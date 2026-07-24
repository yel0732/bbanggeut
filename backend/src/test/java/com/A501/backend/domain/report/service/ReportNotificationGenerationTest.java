package com.A501.backend.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.A501.backend.domain.report.entity.DailyReport;
import com.A501.backend.domain.report.entity.ReportGenerationStatus;
import com.A501.backend.domain.report.entity.ReportNotificationStatus;
import com.A501.backend.domain.report.entity.ReportNotificationType;
import com.A501.backend.domain.report.model.ReportGameSource;
import com.A501.backend.domain.report.model.ReportPeriod;
import com.A501.backend.domain.report.port.ConversationSummaryPort;
import com.A501.backend.domain.report.port.GameReportQueryPort;
import com.A501.backend.domain.report.port.ReportNotificationRecipientPort;
import com.A501.backend.domain.report.repository.DailyReportRepository;
import com.A501.backend.domain.report.repository.ReportConversationSummaryRepository;
import com.A501.backend.domain.report.repository.ReportGameResultRepository;
import com.A501.backend.domain.report.repository.ReportGenerationAttemptRepository;
import com.A501.backend.domain.report.repository.ReportManualRegenerationRepository;
import com.A501.backend.domain.report.repository.ReportNotificationFailureRepository;
import com.A501.backend.domain.report.repository.ReportNotificationRepository;

@SpringBootTest(properties = {
        "report.schedule.enabled=false",
        "report.retry.max-attempts=3",
        "report.retry.interval=PT0S"
})
class ReportNotificationGenerationTest {

    private static final Long PARENT_ID = 1L;
    private static final Long CHILD_ID = 15L;
    private static final LocalDate REPORT_DATE = LocalDate.of(2026, 7, 23);
    private static final LocalDateTime STARTED_AT =
            LocalDateTime.of(2026, 7, 22, 19, 0);
    private static final LocalDateTime ENDED_AT =
            LocalDateTime.of(2026, 7, 23, 19, 0);

    @Autowired
    private DailyReportGenerationService generationService;

    @Autowired
    private ReportNotificationPublisher notificationPublisher;

    @Autowired
    private DailyReportRepository dailyReportRepository;

    @Autowired
    private ReportGenerationAttemptRepository attemptRepository;

    @Autowired
    private ReportGameResultRepository gameResultRepository;

    @Autowired
    private ReportConversationSummaryRepository conversationSummaryRepository;

    @Autowired
    private ReportManualRegenerationRepository manualRegenerationRepository;

    @Autowired
    private ReportNotificationRepository notificationRepository;

    @Autowired
    private ReportNotificationFailureRepository failureRepository;

    @MockitoBean
    private GameReportQueryPort gameReportQueryPort;

    @MockitoBean
    private ConversationSummaryPort conversationSummaryPort;

    @MockitoBean
    private ReportNotificationRecipientPort recipientPort;

    @BeforeEach
    void setUp() {
        failureRepository.deleteAll();
        notificationRepository.deleteAll();
        conversationSummaryRepository.deleteAll();
        gameResultRepository.deleteAll();
        attemptRepository.deleteAll();
        manualRegenerationRepository.deleteAll();
        dailyReportRepository.deleteAll();
        when(recipientPort.findParentId(CHILD_ID)).thenReturn(PARENT_ID);
    }

    @Test
    void completedReportCreatesPrivacySafeNotification() {
        DailyReport report = createPendingReport();
        when(gameReportQueryPort.findCompletedGames(
                CHILD_ID, STARTED_AT, ENDED_AT))
                .thenReturn(List.of(validGame()));
        when(conversationSummaryPort.generateSummary(
                CHILD_ID, STARTED_AT, ENDED_AT))
                .thenReturn(Optional.empty());

        generationService.generate(report.getId());

        assertThat(notificationRepository
                .findAllByReportIdOrderByIdAsc(report.getId()))
                .singleElement()
                .satisfies(notification -> {
                    assertThat(notification.getParentId()).isEqualTo(PARENT_ID);
                    assertThat(notification.getChildId()).isEqualTo(CHILD_ID);
                    assertThat(notification.getReportDate())
                            .isEqualTo(REPORT_DATE);
                    assertThat(notification.getNotificationType())
                            .isEqualTo(
                                    ReportNotificationType.REPORT_COMPLETED);
                    assertThat(notification.getNotificationStatus())
                            .isEqualTo(ReportNotificationStatus.UNREAD);
                    assertThat(notification.getMessage())
                            .isEqualTo(
                                    ReportNotificationPersistenceService
                                            .COMPLETED_MESSAGE)
                            .doesNotContain("DOBBLE", "3500", "conversation");
                });
        assertThat(failureRepository.findAll()).isEmpty();
    }

    @Test
    void onlyFinalAutomaticRetryFailureCreatesFailureNotification() {
        DailyReport report = createPendingReport();
        when(gameReportQueryPort.findCompletedGames(
                CHILD_ID, STARTED_AT, ENDED_AT))
                .thenThrow(new IllegalStateException(
                        "private generation failure detail"));

        assertThatThrownBy(() -> generationService.generate(report.getId()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(dailyReportRepository.findById(report.getId())
                .orElseThrow()
                .getGenerationStatus())
                .isEqualTo(ReportGenerationStatus.FAILED);
        assertThat(attemptRepository
                .findAllByReportIdOrderByAttemptNumberAsc(report.getId()))
                .hasSize(3);
        assertThat(notificationRepository
                .findAllByReportIdOrderByIdAsc(report.getId()))
                .singleElement()
                .satisfies(notification -> {
                    assertThat(notification.getNotificationType())
                            .isEqualTo(ReportNotificationType.REPORT_FAILED);
                    assertThat(notification.getMessage())
                            .isEqualTo(
                                    ReportNotificationPersistenceService
                                            .FAILED_MESSAGE)
                            .doesNotContain(
                                    "private generation failure detail");
                });
    }

    @Test
    void notificationFailureDoesNotChangeCompletedReportStatus() {
        DailyReport report = createPendingReport();
        when(gameReportQueryPort.findCompletedGames(
                CHILD_ID, STARTED_AT, ENDED_AT))
                .thenReturn(List.of(validGame()));
        when(conversationSummaryPort.generateSummary(
                CHILD_ID, STARTED_AT, ENDED_AT))
                .thenReturn(Optional.empty());
        when(recipientPort.findParentId(CHILD_ID))
                .thenThrow(new IllegalStateException(
                        "recipient service unavailable"));

        generationService.generate(report.getId());

        assertThat(dailyReportRepository.findById(report.getId())
                .orElseThrow()
                .getGenerationStatus())
                .isEqualTo(ReportGenerationStatus.COMPLETED);
        assertThat(notificationRepository.findAll()).isEmpty();
        assertThat(failureRepository
                .findAllByReportIdOrderByIdAsc(report.getId()))
                .singleElement()
                .satisfies(failure -> {
                    assertThat(failure.getNotificationType())
                            .isEqualTo(
                                    ReportNotificationType.REPORT_COMPLETED);
                    assertThat(failure.getFailureReason())
                            .isEqualTo("recipient service unavailable");
                });
    }

    @Test
    void repeatedFinalFailurePublicationCreatesOneNotification() {
        DailyReport report = createFailedReport();

        notificationPublisher.publishFinalFailure(report.getId());
        notificationPublisher.publishFinalFailure(report.getId());

        assertThat(notificationRepository
                .findAllByReportIdOrderByIdAsc(report.getId()))
                .singleElement()
                .satisfies(notification ->
                        assertThat(notification.getNotificationType())
                                .isEqualTo(
                                        ReportNotificationType.REPORT_FAILED));
        assertThat(failureRepository.findAll()).isEmpty();
    }

    @Test
    void concurrentCompletedPublicationCreatesOneNotification()
            throws Exception {
        DailyReport report = createCompletedReport();
        int requestCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = java.util.stream.IntStream
                    .range(0, requestCount)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        notificationPublisher.publishCompleted(report.getId());
                        return null;
                    }))
                    .toList();

            ready.await();
            start.countDown();
            for (Future<Object> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(notificationRepository
                .findAllByReportIdOrderByIdAsc(report.getId()))
                .singleElement()
                .satisfies(notification ->
                        assertThat(notification.getNotificationType())
                                .isEqualTo(
                                        ReportNotificationType.REPORT_COMPLETED));
        assertThat(failureRepository.findAll()).isEmpty();
    }

    private DailyReport createPendingReport() {
        return dailyReportRepository.saveAndFlush(DailyReport.pending(
                CHILD_ID,
                new ReportPeriod(STARTED_AT, ENDED_AT, REPORT_DATE)));
    }

    private DailyReport createCompletedReport() {
        DailyReport report = DailyReport.pending(
                CHILD_ID,
                new ReportPeriod(STARTED_AT, ENDED_AT, REPORT_DATE));
        report.startProcessing(ENDED_AT.plusMinutes(1));
        report.complete(ENDED_AT.plusMinutes(2));
        return dailyReportRepository.saveAndFlush(report);
    }

    private DailyReport createFailedReport() {
        DailyReport report = DailyReport.pending(
                CHILD_ID,
                new ReportPeriod(STARTED_AT, ENDED_AT, REPORT_DATE));
        report.startProcessing(ENDED_AT.plusMinutes(1));
        report.fail(ENDED_AT.plusMinutes(2), "generation failed");
        return dailyReportRepository.saveAndFlush(report);
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
