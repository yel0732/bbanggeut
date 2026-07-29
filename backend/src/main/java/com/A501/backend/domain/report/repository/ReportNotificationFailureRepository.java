package com.A501.backend.domain.report.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.A501.backend.domain.report.entity.ReportNotificationFailure;

public interface ReportNotificationFailureRepository
        extends JpaRepository<ReportNotificationFailure, Long> {

    List<ReportNotificationFailure> findAllByReportIdOrderByIdAsc(
            Long reportId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ReportNotificationFailure failure where failure.reportId in :reportIds")
    int deleteByReportIds(@Param("reportIds") Collection<Long> reportIds);
}
