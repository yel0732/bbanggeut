package com.A501.backend.domain.report.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.report.dto.response.ReportGenerationResponse;
import com.A501.backend.domain.report.entity.DailyReport;
import com.A501.backend.domain.report.exception.ReportApiErrorCode;
import com.A501.backend.domain.report.model.DailyReportCandidate;
import com.A501.backend.domain.report.model.ReportPeriod;
import com.A501.backend.domain.report.port.ReportChildAccessVerifier;
import com.A501.backend.domain.report.port.ReportCurrentParentProvider;
import com.A501.backend.domain.report.repository.DailyReportRepository;

@Service
public class ReportManualGenerationService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DailyReportRegistrationService registrationService;
    private final DailyReportRepository dailyReportRepository;
    private final ObjectProvider<ReportCurrentParentProvider> currentParentProvider;
    private final ObjectProvider<ReportChildAccessVerifier> childAccessVerifier;
    private final Clock clock;

    public ReportManualGenerationService(
            DailyReportRegistrationService registrationService,
            DailyReportRepository dailyReportRepository,
            ObjectProvider<ReportCurrentParentProvider> currentParentProvider,
            ObjectProvider<ReportChildAccessVerifier> childAccessVerifier,
            @Qualifier("reportClock") Clock clock) {
        this.registrationService = registrationService;
        this.dailyReportRepository = dailyReportRepository;
        this.currentParentProvider = currentParentProvider;
        this.childAccessVerifier = childAccessVerifier;
        this.clock = clock;
    }

    public ReportGenerationResponse generate(Long childId, LocalDate reportDate) {
        Long parentId = required(currentParentProvider).getCurrentParentId();
        required(childAccessVerifier).verifyOwnership(parentId, childId);

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), KST)
                .truncatedTo(ChronoUnit.MICROS);
        if (!reportDate.equals(now.toLocalDate())) {
            throw new BusinessException(ReportApiErrorCode.REPORT_DATE_NOT_TODAY);
        }

        ReportPeriod period = new ReportPeriod(now.minusDays(1), now, reportDate);
        boolean registered = registrationService.register(
                new DailyReportCandidate(childId, period));
        if (!registered) {
            throw new BusinessException(ReportApiErrorCode.REPORT_ALREADY_EXISTS);
        }

        DailyReport report = dailyReportRepository
                .findByChildIdAndReportDate(childId, reportDate)
                .orElseThrow(() -> new BusinessException(
                        ReportApiErrorCode.REPORT_NOT_FOUND));
        return new ReportGenerationResponse(
                report.getId(),
                report.getChildId(),
                report.getReportDate(),
                report.getPeriodStartedAt(),
                report.getPeriodEndedAt(),
                report.getGenerationStatus());
    }

    private <T> T required(ObjectProvider<T> provider) {
        T port = provider.getIfAvailable();
        if (port == null) {
            throw new BusinessException(ReportApiErrorCode.UNAUTHORIZED);
        }
        return port;
    }
}
