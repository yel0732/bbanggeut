package com.A501.backend.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import com.A501.backend.domain.report.entity.DailyReport;
import com.A501.backend.domain.report.entity.ReportGenerationStatus;
import com.A501.backend.domain.report.entity.ReportNotification;
import com.A501.backend.domain.report.entity.ReportNotificationPreference;
import com.A501.backend.domain.report.entity.ReportNotificationType;
import com.A501.backend.domain.report.event.ReportNotificationCreatedEvent;
import com.A501.backend.domain.report.port.ReportNotificationRecipientPort;
import com.A501.backend.domain.report.port.ReportNotificationRecipientPort.ReportNotificationRecipient;
import com.A501.backend.domain.report.repository.DailyReportRepository;
import com.A501.backend.domain.report.repository.ReportNotificationPreferenceRepository;
import com.A501.backend.domain.report.repository.ReportNotificationRepository;

@ExtendWith(MockitoExtension.class)
class ReportNotificationSchedulingTest {

    @Mock
    private DailyReportRepository dailyReportRepository;
    @Mock
    private ReportNotificationRepository notificationRepository;
    @Mock
    private ReportNotificationPreferenceRepository preferenceRepository;
    @Mock
    private ObjectProvider<ReportNotificationRecipientPort> recipientProvider;
    @Mock
    private ReportNotificationRecipientPort recipientPort;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private DailyReport report;

    @Test
    void schedulesNotificationForParentsConfiguredTime() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-29T10:00:00Z"),
                ZoneOffset.UTC);
        ReportNotificationPreference preference =
                ReportNotificationPreference.defaults(1L);
        preference.update(true, LocalTime.of(20, 0), "Asia/Seoul");

        when(dailyReportRepository.findById(10L)).thenReturn(Optional.of(report));
        when(report.getGenerationStatus()).thenReturn(ReportGenerationStatus.COMPLETED);
        when(report.getId()).thenReturn(10L);
        when(report.getChildId()).thenReturn(20L);
        when(report.getReportDate()).thenReturn(LocalDate.of(2026, 7, 29));
        when(notificationRepository.existsByReportIdAndNotificationType(
                10L, ReportNotificationType.REPORT_COMPLETED)).thenReturn(false);
        when(recipientProvider.getIfAvailable()).thenReturn(recipientPort);
        when(recipientPort.findRecipient(20L))
                .thenReturn(new ReportNotificationRecipient(1L, "아이"));
        when(preferenceRepository.findByParentId(1L))
                .thenReturn(Optional.of(preference));
        when(notificationRepository.save(any(ReportNotification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service(clock).create(10L, ReportNotificationType.REPORT_COMPLETED);

        ArgumentCaptor<ReportNotification> captor =
                ArgumentCaptor.forClass(ReportNotification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getScheduledAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 29, 11, 0));
        assertThat(captor.getValue().getDeliveredAt()).isNull();
        verify(eventPublisher, never()).publishEvent(
                any(ReportNotificationCreatedEvent.class));
    }

    @Test
    void changingPreferenceAppliesFromNextReportCycle() {
        ReportNotificationPreference preference =
                ReportNotificationPreference.defaults(1L);
        ReportNotification pending = ReportNotification.create(
                1L,
                10L,
                20L,
                LocalDate.of(2026, 7, 29),
                ReportNotificationType.REPORT_COMPLETED,
                "리포트가 준비되었습니다.",
                LocalDateTime.of(2026, 7, 29, 11, 0));
        when(preferenceRepository.findByParentId(1L))
                .thenReturn(Optional.of(preference));
        when(preferenceRepository.save(preference)).thenReturn(preference);
        new ReportNotificationPreferenceService(
                preferenceRepository)
                .update(1L, true, LocalTime.of(21, 0), "Asia/Seoul");

        assertThat(pending.getScheduledAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 29, 11, 0));
    }

    @Test
    void disabledPreferenceDoesNotCreateNotificationRecord() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-29T10:00:00Z"),
                ZoneOffset.UTC);
        ReportNotificationPreference preference =
                ReportNotificationPreference.defaults(1L);
        preference.update(false, LocalTime.of(20, 0), "Asia/Seoul");

        when(dailyReportRepository.findById(10L)).thenReturn(Optional.of(report));
        when(report.getGenerationStatus()).thenReturn(ReportGenerationStatus.COMPLETED);
        when(report.getChildId()).thenReturn(20L);
        when(notificationRepository.existsByReportIdAndNotificationType(
                10L, ReportNotificationType.REPORT_COMPLETED)).thenReturn(false);
        when(recipientProvider.getIfAvailable()).thenReturn(recipientPort);
        when(recipientPort.findRecipient(20L))
                .thenReturn(new ReportNotificationRecipient(1L, "child"));
        when(preferenceRepository.findByParentId(1L))
                .thenReturn(Optional.of(preference));

        service(clock).create(10L, ReportNotificationType.REPORT_COMPLETED);

        verify(notificationRepository, never()).save(any(ReportNotification.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void missingPreferenceUsesDefaultDeliveryTimeInsteadOfImmediateDelivery() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-29T10:00:00Z"),
                ZoneOffset.UTC);
        ReportNotificationScheduleCalculator calculator =
                new ReportNotificationScheduleCalculator(clock);

        ReportNotificationScheduleCalculator.DeliveryPlan plan =
                calculator.calculate(Optional.empty());

        assertThat(plan.enabled()).isTrue();
        assertThat(plan.deliverNow()).isFalse();
        assertThat(plan.scheduledAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 29, 11, 0));
    }

    private ReportNotificationPersistenceService service(Clock clock) {
        return new ReportNotificationPersistenceService(
                dailyReportRepository,
                notificationRepository,
                preferenceRepository,
                recipientProvider,
                eventPublisher,
                new ReportNotificationScheduleCalculator(clock));
    }
}
