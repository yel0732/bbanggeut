package com.A501.backend.domain.report.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.A501.backend.domain.report.entity.ReportConversationSummary;

public interface ReportConversationSummaryRepository
        extends JpaRepository<ReportConversationSummary, Long> {

    Optional<ReportConversationSummary> findByReportId(Long reportId);

    boolean existsByReportId(Long reportId);

    @Query("""
            select summary.reportId
            from ReportConversationSummary summary
            where summary.reportId in :reportIds
            """)
    List<Long> findReportIdsWithConversationData(Collection<Long> reportIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ReportConversationSummary summary where summary.reportId in :reportIds")
    int deleteByReportIds(@Param("reportIds") Collection<Long> reportIds);
}
