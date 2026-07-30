package com.A501.backend.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.A501.backend.domain.report.entity.ReportNotification;
import com.A501.backend.domain.report.entity.ReportNotificationStatus;
import com.A501.backend.domain.report.entity.ReportNotificationType;
import com.A501.backend.domain.report.repository.ReportNotificationRepository;

@ExtendWith(MockitoExtension.class)
class ReportNotificationQueryServiceTest {

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 7, 30, 11, 0);

    @Mock
    private ReportNotificationRepository notificationRepository;

    private ReportNotificationQueryService service;

    @BeforeEach
    void setUp() {
        service = new ReportNotificationQueryService(
                notificationRepository,
                Clock.fixed(
                        Instant.parse("2026-07-30T11:00:00Z"),
                        ZoneOffset.UTC));
    }

    @Test
    void returnsAllAvailableUnreadNotifications() {
        ReportNotification notification = notification(10L);
        when(notificationRepository
                .findAllByParentIdAndNotificationStatusAndScheduledAtIsNotNullAndScheduledAtLessThanEqualOrderByScheduledAtDescIdDesc(
                        1L,
                        ReportNotificationStatus.UNREAD,
                        NOW))
                .thenReturn(List.of(notification));

        var response = service.getAvailableUnread(1L);

        assertThat(response.unreadCount()).isEqualTo(1);
        assertThat(response.notifications()).singleElement()
                .satisfies(item -> {
                    assertThat(item.reportId()).isEqualTo(10L);
                    assertThat(item.childId()).isEqualTo(20L);
                });
    }

    @Test
    void marksNotificationReadForItsParent() {
        ReportNotification notification = notification(10L);
        when(notificationRepository.findByIdAndParentId(30L, 1L))
                .thenReturn(Optional.of(notification));

        service.markRead(1L, 30L);

        assertThat(notification.getNotificationStatus())
                .isEqualTo(ReportNotificationStatus.READ);
        assertThat(notification.getReadAt()).isEqualTo(NOW);
    }

    @Test
    void viewingReportMarksItsUnreadNotificationRead() {
        ReportNotification notification = notification(10L);
        when(notificationRepository
                .findByReportIdAndParentIdAndNotificationStatus(
                        10L, 1L, ReportNotificationStatus.UNREAD))
                .thenReturn(Optional.of(notification));

        service.markReportRead(1L, 10L);

        assertThat(notification.getNotificationStatus())
                .isEqualTo(ReportNotificationStatus.READ);
        verify(notificationRepository)
                .findByReportIdAndParentIdAndNotificationStatus(
                        10L, 1L, ReportNotificationStatus.UNREAD);
    }

    private ReportNotification notification(Long reportId) {
        return ReportNotification.create(
                1L,
                reportId,
                20L,
                LocalDate.of(2026, 7, 30),
                ReportNotificationType.REPORT_COMPLETED,
                "리포트가 준비되었습니다.",
                NOW.minusMinutes(5));
    }
}
