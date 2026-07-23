package com.A501.backend.domain.report.repository;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;

import com.A501.backend.domain.report.entity.DailyReport;

public interface DailyReportRepository extends JpaRepository<DailyReport, Long> {

    boolean existsByChildIdAndReportDate(Long childId, LocalDate reportDate);
}
