package com.A501.backend.domain.report.service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.report.entity.DailyReport;
import com.A501.backend.domain.report.entity.ReportConversationSummary;
import com.A501.backend.domain.report.entity.ReportGameResult;
import com.A501.backend.domain.report.model.DailyReportGenerationResult;
import com.A501.backend.domain.report.model.ReportConversationSummarySource;
import com.A501.backend.domain.report.model.ReportGameSource;
import com.A501.backend.domain.report.repository.DailyReportRepository;
import com.A501.backend.domain.report.repository.ReportConversationSummaryRepository;
import com.A501.backend.domain.report.repository.ReportGameResultRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DailyReportPersistenceService {

    private final DailyReportRepository dailyReportRepository;
    private final ReportGameResultRepository reportGameResultRepository;
    private final ReportConversationSummaryRepository conversationSummaryRepository;
    private final ReportGameResultMapper gameResultMapper;
    private final ReportConversationSummaryMapper conversationSummaryMapper;

    @Transactional
    public DailyReportGenerationResult persist(
            Long reportId,
            List<ReportGameSource> gameSources,
            Optional<ReportConversationSummarySource> conversationSummary
    ) {
        DailyReport report = dailyReportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Daily report not found."));

        List<ReportGameResult> newGameResults =
                createGameSnapshots(reportId, gameSources);
        reportGameResultRepository.saveAll(newGameResults);

        boolean summaryCreated = createConversationSummary(
                reportId, conversationSummary);

        report.complete();

        return new DailyReportGenerationResult(
                reportId,
                newGameResults.size(),
                summaryCreated);
    }

    private List<ReportGameResult> createGameSnapshots(
            Long reportId,
            List<ReportGameSource> gameSources
    ) {
        Set<UUID> registeredSessionIds = new HashSet<>();
        reportGameResultRepository.findAllByReportId(reportId)
                .forEach(result ->
                        registeredSessionIds.add(result.getGameSessionId()));

        return gameSources.stream()
                .filter(source ->
                        registeredSessionIds.add(source.gameSessionId()))
                .map(source ->
                        gameResultMapper.toSnapshot(reportId, source))
                .toList();
    }

    private boolean createConversationSummary(
            Long reportId,
            Optional<ReportConversationSummarySource> summarySource
    ) {
        if (summarySource.isEmpty()
                || conversationSummaryRepository.existsByReportId(reportId)) {
            return false;
        }

        ReportConversationSummary snapshot =
                conversationSummaryMapper.toSnapshot(
                        reportId, summarySource.orElseThrow());
        conversationSummaryRepository.save(snapshot);
        return true;
    }
}
