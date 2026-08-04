package com.A501.backend.domain.report.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.A501.backend.domain.report.entity.AdminReportGenerationAudit;

public interface AdminReportGenerationAuditRepository
        extends JpaRepository<AdminReportGenerationAudit, Long> {
}
