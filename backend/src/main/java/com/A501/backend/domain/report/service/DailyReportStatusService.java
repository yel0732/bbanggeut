package com.A501.backend.domain.report.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.report.entity.DailyReport;
import com.A501.backend.domain.report.model.DailyReportGenerationTarget;
import com.A501.backend.domain.report.repository.DailyReportRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DailyReportStatusService {

    private final DailyReportRepository dailyReportRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DailyReportGenerationTarget startProcessing(Long reportId) {
        DailyReport report = findForUpdate(reportId);
        report.startProcessing();

        return new DailyReportGenerationTarget(
                report.getId(),
                report.getChildId(),
                report.getPeriodStartedAt(),
                report.getPeriodEndedAt());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long reportId) {
        DailyReport report = findForUpdate(reportId);
        report.fail();
    }

    private DailyReport findForUpdate(Long reportId) {
        return dailyReportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Daily report not found."));
    }
}
