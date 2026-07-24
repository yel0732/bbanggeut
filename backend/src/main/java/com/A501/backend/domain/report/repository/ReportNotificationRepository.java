package com.A501.backend.domain.report.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.A501.backend.domain.report.entity.ReportNotification;
import com.A501.backend.domain.report.entity.ReportNotificationType;

public interface ReportNotificationRepository
        extends JpaRepository<ReportNotification, Long> {

    List<ReportNotification> findAllByReportIdOrderByIdAsc(Long reportId);

    boolean existsByReportIdAndNotificationType(
            Long reportId,
            ReportNotificationType notificationType);

    List<ReportNotification> findByParentIdAndIdGreaterThanOrderByIdAsc(
            Long parentId,
            Long id);
}
