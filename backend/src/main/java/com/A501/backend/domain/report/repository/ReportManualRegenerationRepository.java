package com.A501.backend.domain.report.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.A501.backend.domain.report.entity.ReportManualRegeneration;
import com.A501.backend.domain.report.entity.ManualRegenerationStatus;

import jakarta.persistence.LockModeType;

public interface ReportManualRegenerationRepository
        extends JpaRepository<ReportManualRegeneration, Long> {

    Optional<ReportManualRegeneration> findByParentIdAndIdempotencyKey(
            Long parentId,
            String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ReportManualRegeneration>
            findFirstByReportIdAndRegenerationStatusOrderByRequestedAtDesc(
                    Long reportId,
                    ManualRegenerationStatus regenerationStatus);
}
