package com.A501.backend.domain.report.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.A501.backend.domain.report.entity.ReportGameResult;

public interface ReportGameResultRepository
        extends JpaRepository<ReportGameResult, Long> {

    List<ReportGameResult> findAllByReportId(Long reportId);

    boolean existsByReportIdAndGameSessionId(
            Long reportId,
            UUID gameSessionId);
}
