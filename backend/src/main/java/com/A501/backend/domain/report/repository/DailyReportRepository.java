package com.A501.backend.domain.report.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.A501.backend.domain.report.entity.DailyReport;
import com.A501.backend.domain.report.entity.ReportGenerationStatus;

import jakarta.persistence.LockModeType;

public interface DailyReportRepository extends JpaRepository<DailyReport, Long> {

    boolean existsByChildIdAndReportDate(Long childId, LocalDate reportDate);

    Optional<DailyReport> findByChildIdAndReportDate(
            Long childId,
            LocalDate reportDate);

    @Query("""
            select report
            from DailyReport report
            where report.childId = :childId
              and (:startedOn is null or report.reportDate >= :startedOn)
              and (:endedOn is null or report.reportDate <= :endedOn)
              and (:status is null or report.generationStatus = :status)
            """)
    Page<DailyReport> findAllForParent(
            @Param("childId") Long childId,
            @Param("startedOn") LocalDate startedOn,
            @Param("endedOn") LocalDate endedOn,
            @Param("status")
            com.A501.backend.domain.report.entity.ReportGenerationStatus status,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select report from DailyReport report where report.id = :reportId")
    Optional<DailyReport> findByIdForUpdate(@Param("reportId") Long reportId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select report
            from DailyReport report
            where report.childId = :childId
              and report.reportDate = :reportDate
            """)
    Optional<DailyReport> findByChildIdAndReportDateForUpdate(
            @Param("childId") Long childId,
            @Param("reportDate") LocalDate reportDate);

    @Query("""
            select report.id
            from DailyReport report
            where report.generationStatus = :status
            order by report.id
            """)
    List<Long> findIdsByGenerationStatusOrderById(
            @Param("status") ReportGenerationStatus status,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select report
            from DailyReport report
            where report.generationStatus = 'PROCESSING'
              and report.processingStartedAt < :staleBefore
            order by report.id
            """)
    List<DailyReport> findRecoverableProcessingForUpdate(
            @Param("staleBefore") LocalDateTime staleBefore,
            Pageable pageable);
}
