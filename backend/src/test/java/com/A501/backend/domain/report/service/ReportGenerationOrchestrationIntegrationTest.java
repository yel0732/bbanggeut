package com.A501.backend.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.A501.backend.domain.report.entity.ReportGenerationStatus;
import com.A501.backend.domain.report.model.DailyReportCandidate;
import com.A501.backend.domain.report.model.ReportGameSource;
import com.A501.backend.domain.report.model.ReportPeriod;
import com.A501.backend.domain.report.port.ConversationSummaryPort;
import com.A501.backend.domain.report.port.GameReportQueryPort;
import com.A501.backend.domain.report.repository.DailyReportRepository;
import com.A501.backend.domain.report.repository.ReportGameResultRepository;

@SpringBootTest(properties = {
        "report.schedule.enabled=false",
        "report.recovery.enabled=false",
        "report.retry.max-attempts=1",
        "report.retry.interval=PT0S"
})
class ReportGenerationOrchestrationIntegrationTest {

    private static final Long CHILD_ID = 15L;
    private static final LocalDateTime STARTED_AT =
            LocalDateTime.of(2026, 7, 22, 19, 0);
    private static final LocalDateTime ENDED_AT =
            LocalDateTime.of(2026, 7, 23, 19, 0);
    private static final LocalDate REPORT_DATE = LocalDate.of(2026, 7, 23);

    @Autowired
    private DailyReportRegistrationService registrationService;

    @Autowired
    private DailyReportRepository reportRepository;

    @Autowired
    private ReportGameResultRepository gameResultRepository;

    @MockitoBean
    private GameReportQueryPort gameReportQueryPort;

    @MockitoBean
    private ConversationSummaryPort conversationSummaryPort;

    @MockitoBean
    private ReportNotificationPublisher notificationPublisher;

    @BeforeEach
    void setUp() {
        gameResultRepository.deleteAll();
        reportRepository.deleteAll();
    }

    @Test
    void registrationCommitAutomaticallyRunsGenerationToCompletion()
            throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        UUID gameSessionId = UUID.randomUUID();
        when(gameReportQueryPort.findCompletedGames(
                CHILD_ID, STARTED_AT, ENDED_AT))
                .thenReturn(List.of(new ReportGameSource(
                        gameSessionId,
                        1L,
                        "DOBBLE",
                        "도블",
                        LocalDateTime.of(2026, 7, 23, 10, 0),
                        180,
                        1000,
                        null)));
        when(conversationSummaryPort.generateSummary(
                CHILD_ID, STARTED_AT, ENDED_AT))
                .thenReturn(Optional.empty());
        doAnswer(invocation -> {
            completed.countDown();
            return null;
        }).when(notificationPublisher).publishCompleted(any());

        boolean registered = registrationService.register(
                new DailyReportCandidate(
                        CHILD_ID,
                        new ReportPeriod(STARTED_AT, ENDED_AT, REPORT_DATE)));

        assertThat(registered).isTrue();
        assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
        var report = reportRepository
                .findByChildIdAndReportDate(CHILD_ID, REPORT_DATE)
                .orElseThrow();
        assertThat(report.getGenerationStatus())
                .isEqualTo(ReportGenerationStatus.COMPLETED);
        assertThat(gameResultRepository.findAllByReportId(report.getId()))
                .hasSize(1);
    }
}
