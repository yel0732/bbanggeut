package com.A501.backend.domain.report.repository;

import java.util.List;
import java.util.Collection;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.A501.backend.domain.report.entity.ReportGameResult;

public interface ReportGameResultRepository
        extends JpaRepository<ReportGameResult, Long> {

    List<ReportGameResult> findAllByReportId(Long reportId);

    List<ReportGameResult> findAllByReportIdOrderByCompletedAtAsc(Long reportId);

    @Query("""
            select distinct result.reportId
            from ReportGameResult result
            where result.reportId in :reportIds
            """)
    List<Long> findReportIdsWithGameData(Collection<Long> reportIds);

    boolean existsByReportIdAndGameSessionId(
            Long reportId,
            UUID gameSessionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ReportGameResult result where result.reportId in :reportIds")
    int deleteByReportIds(@Param("reportIds") Collection<Long> reportIds);
}
