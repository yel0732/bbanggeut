package com.A501.backend.domain.report.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.A501.backend.domain.report.entity.DailyReport;

import jakarta.persistence.LockModeType;

public interface DailyReportRepository extends JpaRepository<DailyReport, Long> {

    boolean existsByChildIdAndReportDate(Long childId, LocalDate reportDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select report from DailyReport report where report.id = :reportId")
    Optional<DailyReport> findByIdForUpdate(@Param("reportId") Long reportId);
}
