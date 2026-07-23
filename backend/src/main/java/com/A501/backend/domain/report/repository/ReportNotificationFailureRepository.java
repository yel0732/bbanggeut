package com.A501.backend.domain.report.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.A501.backend.domain.report.entity.ReportNotificationFailure;

public interface ReportNotificationFailureRepository
        extends JpaRepository<ReportNotificationFailure, Long> {

    List<ReportNotificationFailure> findAllByReportIdOrderByIdAsc(
            Long reportId);
}
