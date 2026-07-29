package com.A501.backend.domain.report.repository;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from ReportManualRegeneration regeneration
            where regeneration.reportId in :reportIds
               or regeneration.parentId = :parentId
            """)
    int deleteByReportIdsOrParentId(
            @Param("reportIds") Collection<Long> reportIds,
            @Param("parentId") Long parentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from ReportManualRegeneration regeneration
            where regeneration.parentId = :parentId
            """)
    int deleteByParentId(@Param("parentId") Long parentId);
}
