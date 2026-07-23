package com.A501.backend.domain.report.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.A501.backend.domain.report.entity.ReportConversationSummary;

public interface ReportConversationSummaryRepository
        extends JpaRepository<ReportConversationSummary, Long> {

    Optional<ReportConversationSummary> findByReportId(Long reportId);

    boolean existsByReportId(Long reportId);
}
