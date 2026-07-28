package com.A501.backend.domain.report.service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.report.dto.response.ReportConversationSummaryResponse;
import com.A501.backend.domain.report.dto.response.ReportDataAvailabilityResponse;
import com.A501.backend.domain.report.dto.response.ReportDetailResponse;
import com.A501.backend.domain.report.dto.response.ReportGameResultResponse;
import com.A501.backend.domain.report.dto.response.ReportListItemResponse;
import com.A501.backend.domain.report.dto.response.ReportListResponse;
import com.A501.backend.domain.report.dto.response.ReportPageResponse;
import com.A501.backend.domain.report.entity.DailyReport;
import com.A501.backend.domain.report.entity.ReportConversationSummary;
import com.A501.backend.domain.report.entity.ReportGenerationStatus;
import com.A501.backend.domain.report.exception.ReportApiErrorCode;
import com.A501.backend.domain.report.port.ReportChildAccessVerifier;
import com.A501.backend.domain.report.port.ReportCurrentParentProvider;
import com.A501.backend.domain.report.repository.DailyReportRepository;
import com.A501.backend.domain.report.repository.ReportConversationSummaryRepository;
import com.A501.backend.domain.report.repository.ReportGameResultRepository;
import com.A501.backend.domain.report.repository.ReportGenerationAttemptRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReportQueryService {

    private final DailyReportRepository dailyReportRepository;
    private final ReportGameResultRepository gameResultRepository;
    private final ReportConversationSummaryRepository summaryRepository;
    private final ReportGenerationAttemptRepository attemptRepository;
    private final ObjectProvider<ReportCurrentParentProvider>
            currentParentProvider;
    private final ObjectProvider<ReportChildAccessVerifier>
            childAccessVerifier;
    private final ObjectMapper objectMapper;

    public ReportListResponse getReports(
            Long childId,
            LocalDate startDate,
            LocalDate endDate,
            ReportGenerationStatus status,
            int page,
            int size
    ) {
        verifyAccess(childId);
        validatePeriod(startDate, endDate);

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "reportDate"));
        Page<DailyReport> reports = dailyReportRepository.findAllForParent(
                childId,
                startDate,
                endDate,
                status,
                pageable);
        List<Long> reportIds = reports.getContent().stream()
                .map(DailyReport::getId)
                .toList();
        Set<Long> gameReportIds = reportIds.isEmpty()
                ? Set.of()
                : new HashSet<>(
                        gameResultRepository.findReportIdsWithGameData(
                                reportIds));
        Set<Long> conversationReportIds = reportIds.isEmpty()
                ? Set.of()
                : new HashSet<>(
                        summaryRepository.findReportIdsWithConversationData(
                                reportIds));
        List<ReportListItemResponse> items = reports.getContent().stream()
                .map(report -> ReportListItemResponse.from(
                        report,
                        availability(
                                report.getId(),
                                gameReportIds,
                                conversationReportIds)))
                .toList();
        return new ReportListResponse(
                childId,
                items,
                ReportPageResponse.from(reports));
    }

    public ReportDetailResponse getReport(
            Long childId,
            LocalDate reportDate
    ) {
        verifyAccess(childId);
        DailyReport report = dailyReportRepository
                .findByChildIdAndReportDate(childId, reportDate)
                .orElseThrow(() ->
                        new BusinessException(ReportApiErrorCode.REPORT_NOT_FOUND));

        if (report.getGenerationStatus() != ReportGenerationStatus.COMPLETED) {
            return ReportDetailResponse.from(
                    report,
                    new ReportDataAvailabilityResponse(false, false),
                    List.of(),
                    null,
                    attemptRepository
                            .findFirstByReportIdOrderByAttemptNumberDesc(
                                    report.getId())
                            .orElse(null));
        }

        List<ReportGameResultResponse> games = gameResultRepository
                .findAllByReportIdOrderByCompletedAtAsc(report.getId())
                .stream()
                .map(result -> ReportGameResultResponse.from(
                        result,
                        deserializeMetrics(result.getResultMetrics())))
                .toList();
        ReportConversationSummaryResponse summary = summaryRepository
                .findByReportId(report.getId())
                .map(this::toResponse)
                .orElse(null);
        return ReportDetailResponse.from(
                report,
                new ReportDataAvailabilityResponse(
                        !games.isEmpty(),
                        summary != null),
                games,
                summary,
                null);
    }

    private void verifyAccess(Long childId) {
        Long parentId = required(currentParentProvider).getCurrentParentId();
        required(childAccessVerifier).verifyOwnership(parentId, childId);
    }

    private void validatePeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null
                && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException(
                    "startDate must be on or before endDate.");
        }
    }

    private ReportDataAvailabilityResponse availability(
            Long reportId,
            Set<Long> gameReportIds,
            Set<Long> conversationReportIds
    ) {
        return new ReportDataAvailabilityResponse(
                gameReportIds.contains(reportId),
                conversationReportIds.contains(reportId));
    }

    private Object deserializeMetrics(String metrics) {
        if (metrics == null || metrics.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(metrics, Object.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Failed to deserialize game result metrics.", exception);
        }
    }

    private ReportConversationSummaryResponse toResponse(
            ReportConversationSummary summary
    ) {
        return ReportConversationSummaryResponse.from(
                summary,
                deserializeList(summary.getSourceConversationIds()),
                deserializeList(summary.getMainEvents()),
                deserializeList(summary.getEmotions()),
                deserializeList(summary.getInterests()));
    }

    private List<String> deserializeList(String value) {
        try {
            return Arrays.asList(objectMapper.readValue(value, String[].class));
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Failed to deserialize conversation summary.", exception);
        }
    }

    private <T> T required(ObjectProvider<T> provider) {
        T port = provider.getIfAvailable();
        if (port == null) {
            throw new BusinessException(ReportApiErrorCode.UNAUTHORIZED);
        }
        return port;
    }
}
