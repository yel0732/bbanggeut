package com.A501.backend.domain.report.entity;

import java.time.LocalDate;

import com.A501.backend.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "admin_report_generation_audit")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminReportGenerationAudit extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long id;

    @Column(name = "admin_parent_id", nullable = false)
    private Long adminParentId;

    @Column(name = "child_id", nullable = false)
    private Long childId;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    private AdminReportGenerationAudit(
            Long adminParentId,
            Long childId,
            Long reportId,
            LocalDate reportDate) {
        this.adminParentId = adminParentId;
        this.childId = childId;
        this.reportId = reportId;
        this.reportDate = reportDate;
    }

    public static AdminReportGenerationAudit create(
            Long adminParentId,
            Long childId,
            Long reportId,
            LocalDate reportDate) {
        if (adminParentId == null || childId == null || reportId == null
                || reportDate == null) {
            throw new IllegalArgumentException("Admin report audit fields are required.");
        }
        return new AdminReportGenerationAudit(
                adminParentId, childId, reportId, reportDate);
    }
}
