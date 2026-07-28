package com.A501.backend.domain.report.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.report.dto.response.ReportRegenerationResponse;
import com.A501.backend.domain.report.entity.DailyReport;
import com.A501.backend.domain.report.entity.ReportGenerationStatus;
import com.A501.backend.domain.report.entity.ReportManualRegeneration;
import com.A501.backend.domain.report.exception.ReportApiErrorCode;
import com.A501.backend.domain.report.event.ReportGenerationRequestedEvent;
import com.A501.backend.domain.report.port.ReportChildAccessVerifier;
import com.A501.backend.domain.report.port.ReportCurrentParentProvider;
import com.A501.backend.domain.report.repository.DailyReportRepository;
import com.A501.backend.domain.report.repository.ReportManualRegenerationRepository;

@Service
public class ReportManualRegenerationService {

    private static final String ACCEPTED_MESSAGE =
            "Report regeneration request has been accepted.";

    private final DailyReportRepository dailyReportRepository;
    private final ReportManualRegenerationRepository regenerationRepository;
    private final ObjectProvider<ReportCurrentParentProvider>
            currentParentProvider;
    private final ObjectProvider<ReportChildAccessVerifier>
            childAccessVerifier;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public ReportManualRegenerationService(
            DailyReportRepository dailyReportRepository,
            ReportManualRegenerationRepository regenerationRepository,
            ObjectProvider<ReportCurrentParentProvider> currentParentProvider,
            ObjectProvider<ReportChildAccessVerifier> childAccessVerifier,
            @Qualifier("utcClock") Clock clock,
            ApplicationEventPublisher eventPublisher
    ) {
        this.dailyReportRepository = dailyReportRepository;
        this.regenerationRepository = regenerationRepository;
        this.currentParentProvider = currentParentProvider;
        this.childAccessVerifier = childAccessVerifier;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ReportRegenerationResponse request(
            Long childId,
            LocalDate reportDate,
            String idempotencyKey
    ) {
        Long parentId = required(currentParentProvider)
                .getCurrentParentId();
        required(childAccessVerifier).verifyOwnership(parentId, childId);

        String normalizedKey = normalize(idempotencyKey);
        var existing = regenerationRepository
                .findByParentIdAndIdempotencyKey(parentId, normalizedKey);
        if (existing.isPresent()) {
            return repeatedResponse(existing.get(), childId, reportDate);
        }

        DailyReport report = dailyReportRepository
                .findByChildIdAndReportDateForUpdate(childId, reportDate)
                .orElseThrow(() ->
                        new BusinessException(ReportApiErrorCode.REPORT_NOT_FOUND));
        existing = regenerationRepository
                .findByParentIdAndIdempotencyKey(parentId, normalizedKey);
        if (existing.isPresent()) {
            return repeatedResponse(existing.get(), childId, reportDate);
        }
        if (report.getGenerationStatus() != ReportGenerationStatus.FAILED) {
            throw new BusinessException(
                    ReportApiErrorCode.REPORT_REGENERATION_NOT_ALLOWED);
        }

        LocalDateTime requestedAt =
                LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        ReportManualRegeneration regeneration =
                ReportManualRegeneration.pending(
                        report.getId(),
                        childId,
                        parentId,
                        normalizedKey,
                        requestedAt,
                        report.getFailureReason());
        try {
            regenerationRepository.saveAndFlush(regeneration);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(
                    ReportApiErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
        report.requestRegeneration();
        eventPublisher.publishEvent(
                new ReportGenerationRequestedEvent(report.getId()));

        return response(report, requestedAt);
    }

    private String normalize(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Idempotency-Key header is required.");
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() > 100) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must not exceed 100 characters.");
        }
        return normalized;
    }

    private ReportRegenerationResponse repeatedResponse(
            ReportManualRegeneration regeneration,
            Long childId,
            LocalDate reportDate
    ) {
        DailyReport report = dailyReportRepository
                .findById(regeneration.getReportId())
                .orElseThrow(() ->
                        new BusinessException(ReportApiErrorCode.REPORT_NOT_FOUND));
        if (!regeneration.getChildId().equals(childId)
                || !report.getReportDate().equals(reportDate)) {
            throw new BusinessException(
                    ReportApiErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
        return response(report, regeneration.getRequestedAt());
    }

    private ReportRegenerationResponse response(
            DailyReport report,
            LocalDateTime requestedAt
    ) {
        return new ReportRegenerationResponse(
                report.getId(),
                report.getChildId(),
                report.getReportDate(),
                ReportGenerationStatus.PENDING,
                requestedAt,
                ACCEPTED_MESSAGE);
    }

    private <T> T required(ObjectProvider<T> provider) {
        T port = provider.getIfAvailable();
        if (port == null) {
            throw new BusinessException(ReportApiErrorCode.UNAUTHORIZED);
        }
        return port;
    }
}
