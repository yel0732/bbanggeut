package com.A501.backend.domain.report.sse;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
import com.A501.backend.domain.report.entity.ReportNotificationType;
import com.A501.backend.domain.report.entity.ReportNotificationPreference;
import com.A501.backend.domain.report.model.ReportGameSource;
import com.A501.backend.domain.report.model.ReportPeriod;
import com.A501.backend.domain.report.port.ConversationSummaryPort;
import com.A501.backend.domain.report.port.GameReportQueryPort;
import com.A501.backend.domain.report.port.ReportNotificationRecipientPort;
import com.A501.backend.domain.report.port.ReportNotificationRecipientPort.ReportNotificationRecipient;
import com.A501.backend.domain.report.repository.DailyReportRepository;
import com.A501.backend.domain.report.repository.ReportNotificationRepository;
import com.A501.backend.domain.report.repository.ReportNotificationPreferenceRepository;
import com.A501.backend.domain.report.service.DailyReportGenerationService;
import com.A501.backend.domain.report.service.ReportNotificationPublisher;

@SpringBootTest(properties = {
        "report.schedule.enabled=false",
        "report.retry.max-attempts=3",
        "report.retry.interval=PT0S"
})
class ReportNotificationSseDeliveryIntegrationTest {

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
    private ReportNotificationRepository notificationRepository;

    @Autowired
    private ReportNotificationPreferenceRepository preferenceRepository;

    @MockitoBean
    private GameReportQueryPort gameReportQueryPort;

    @MockitoBean
    private ConversationSummaryPort conversationSummaryPort;

    @MockitoBean
    private ReportNotificationRecipientPort recipientPort;

    @MockitoSpyBean
    private ReportNotificationStreamService streamService;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        preferenceRepository.deleteAll();
        dailyReportRepository.deleteAll();
        ReportNotificationPreference preference =
                ReportNotificationPreference.defaults(PARENT_ID);
        preference.update(true, LocalTime.MIDNIGHT, "Asia/Seoul");
        preferenceRepository.saveAndFlush(preference);
        when(recipientPort.findRecipient(CHILD_ID))
                .thenReturn(new ReportNotificationRecipient(PARENT_ID, "하준"));
    }

    @Test
    void completedReportDeliversAfterCommit() {
        DailyReport report = createPendingReport();
        when(gameReportQueryPort.findCompletedGames(
                CHILD_ID, STARTED_AT, ENDED_AT))
                .thenReturn(List.of(validGame()));
        when(conversationSummaryPort.generateSummary(
                CHILD_ID, STARTED_AT, ENDED_AT))
                .thenReturn(Optional.empty());

        generationService.generate(report.getId());

        // 커밋 이후 리스너가 완료 이벤트를 정확히 한 번 전달한다.
        verify(streamService, times(1)).deliver(argThat(event ->
                event.parentId().equals(PARENT_ID)
                        && event.notificationType()
                                == ReportNotificationType.REPORT_COMPLETED));
    }

    @Test
    void duplicatePublicationDeliversOnce() {
        DailyReport report = createCompletedReport();

        notificationPublisher.publishCompleted(report.getId());
        notificationPublisher.publishCompleted(report.getId());

        // 중복 요청은 새 알림을 만들지 않으므로 SSE 전달도 한 번뿐이다.
        verify(streamService, times(1)).deliver(argThat(event ->
                event.notificationType()
                        == ReportNotificationType.REPORT_COMPLETED));
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
