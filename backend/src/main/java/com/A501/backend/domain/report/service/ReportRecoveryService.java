package com.A501.backend.domain.report.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.report.entity.DailyReport;
import com.A501.backend.domain.report.entity.ReportGenerationStatus;
import com.A501.backend.domain.report.event.ReportGenerationRequestedEvent;
import com.A501.backend.domain.report.model.ReportRecoveryProperties;
import com.A501.backend.domain.report.repository.DailyReportRepository;

@Service
public class ReportRecoveryService {

    private final DailyReportRepository repository;
    private final ReportRecoveryProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ReportRecoveryService(
            DailyReportRepository repository,
            ReportRecoveryProperties properties,
            ApplicationEventPublisher eventPublisher,
            @Qualifier("utcClock") Clock clock
    ) {
        this.repository = repository;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public int recoverAndRequest() {
        PageRequest limit = PageRequest.of(0, properties.batchSize());
        LocalDateTime staleBefore = LocalDateTime.now(clock)
                .minus(properties.staleProcessingThreshold())
                .truncatedTo(ChronoUnit.MICROS);

        List<DailyReport> staleReports = repository
                .findRecoverableProcessingForUpdate(staleBefore, limit);
        staleReports.forEach(DailyReport::recoverInterruptedProcessing);

        List<Long> reportIds = repository
                .findIdsByGenerationStatusOrderById(
                        ReportGenerationStatus.PENDING,
                        limit)
                .stream()
                .distinct()
                .toList();
        reportIds.forEach(reportId -> eventPublisher.publishEvent(
                new ReportGenerationRequestedEvent(reportId)));
        return reportIds.size();
    }
}
