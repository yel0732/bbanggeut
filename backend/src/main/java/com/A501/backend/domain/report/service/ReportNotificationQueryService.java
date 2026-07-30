package com.A501.backend.domain.report.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.report.dto.response.ReportNotificationItemResponse;
import com.A501.backend.domain.report.dto.response.UnreadReportNotificationsResponse;
import com.A501.backend.domain.report.entity.ReportNotification;
import com.A501.backend.domain.report.entity.ReportNotificationStatus;
import com.A501.backend.domain.report.exception.ReportApiErrorCode;
import com.A501.backend.domain.report.repository.ReportNotificationRepository;

@Service
@Transactional(readOnly = true)
public class ReportNotificationQueryService {

    private final ReportNotificationRepository notificationRepository;
    private final Clock clock;

    public ReportNotificationQueryService(
            ReportNotificationRepository notificationRepository,
            @Qualifier("utcClock") Clock clock) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    public UnreadReportNotificationsResponse getAvailableUnread(Long parentId) {
        var notifications = notificationRepository
                .findAllByParentIdAndNotificationStatusAndScheduledAtIsNotNullAndScheduledAtLessThanEqualOrderByScheduledAtDescIdDesc(
                        parentId,
                        ReportNotificationStatus.UNREAD,
                        LocalDateTime.now(clock));
        var items = notifications.stream()
                .map(ReportNotificationItemResponse::from)
                .toList();
        return new UnreadReportNotificationsResponse(items.size(), items);
    }

    @Transactional
    public void markRead(Long parentId, Long notificationId) {
        ReportNotification notification = notificationRepository
                .findByIdAndParentId(notificationId, parentId)
                .orElseThrow(() -> new BusinessException(
                        ReportApiErrorCode.REPORT_NOT_FOUND));
        notification.markRead(LocalDateTime.now(clock));
    }

    @Transactional
    public void markReportRead(Long parentId, Long reportId) {
        notificationRepository
                .findByReportIdAndParentIdAndNotificationStatus(
                        reportId,
                        parentId,
                        ReportNotificationStatus.UNREAD)
                .ifPresent(notification ->
                        notification.markRead(LocalDateTime.now(clock)));
    }
}
