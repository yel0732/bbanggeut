package com.A501.backend.domain.report.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.A501.backend.domain.report.entity.ReportGenerationAttempt;

public interface ReportGenerationAttemptRepository
        extends JpaRepository<ReportGenerationAttempt, Long> {

    List<ReportGenerationAttempt> findAllByReportIdOrderByAttemptNumberAsc(
            Long reportId);

    Optional<ReportGenerationAttempt>
            findFirstByReportIdOrderByAttemptNumberDesc(Long reportId);

    @Query("""
            select coalesce(max(attempt.attemptNumber), 0)
            from ReportGenerationAttempt attempt
            where attempt.reportId = :reportId
            """)
    int findMaxAttemptNumberByReportId(@Param("reportId") Long reportId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ReportGenerationAttempt attempt where attempt.reportId in :reportIds")
    int deleteByReportIds(@Param("reportIds") Collection<Long> reportIds);
}
