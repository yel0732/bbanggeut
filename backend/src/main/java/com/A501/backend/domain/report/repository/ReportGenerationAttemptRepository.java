package com.A501.backend.domain.report.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.A501.backend.domain.report.entity.ReportGenerationAttempt;

public interface ReportGenerationAttemptRepository
        extends JpaRepository<ReportGenerationAttempt, Long> {

    List<ReportGenerationAttempt> findAllByReportIdOrderByAttemptNumberAsc(
            Long reportId);

    @Query("""
            select coalesce(max(attempt.attemptNumber), 0)
            from ReportGenerationAttempt attempt
            where attempt.reportId = :reportId
            """)
    int findMaxAttemptNumberByReportId(@Param("reportId") Long reportId);
}
