package com.A501.backend.domain.report.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.A501.backend.domain.report.entity.ReportNotification;

public interface ReportNotificationRepository
        extends JpaRepository<ReportNotification, Long> {

    List<ReportNotification> findAllByReportIdOrderByIdAsc(Long reportId);
}
