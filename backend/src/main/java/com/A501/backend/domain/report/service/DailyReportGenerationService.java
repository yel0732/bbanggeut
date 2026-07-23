package com.A501.backend.domain.report.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.A501.backend.domain.report.model.DailyReportGenerationResult;
import com.A501.backend.domain.report.model.DailyReportGenerationTarget;
import com.A501.backend.domain.report.model.ReportConversationSummarySource;
import com.A501.backend.domain.report.model.ReportGameSource;
import com.A501.backend.domain.report.port.ConversationSummaryPort;
import com.A501.backend.domain.report.port.GameReportQueryPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DailyReportGenerationService {

    private final DailyReportStatusService statusService;
    private final DailyReportPersistenceService persistenceService;
    private final ObjectProvider<GameReportQueryPort> gameReportQueryPortProvider;
    private final ObjectProvider<ConversationSummaryPort> conversationSummaryPortProvider;

    public DailyReportGenerationResult generate(Long reportId) {
        DailyReportGenerationTarget target =
                statusService.startProcessing(reportId);

        try {
            GameReportQueryPort gameReportQueryPort =
                    requiredPort(
                            gameReportQueryPortProvider,
                            "GameReportQueryPort is not connected.");
            ConversationSummaryPort conversationSummaryPort =
                    requiredPort(
                            conversationSummaryPortProvider,
                            "ConversationSummaryPort is not connected.");
            List<ReportGameSource> gameSources =
                    gameReportQueryPort.findCompletedGames(
                            target.childId(),
                            target.startedAt(),
                            target.endedAt());
            Optional<ReportConversationSummarySource> conversationSummary =
                    conversationSummaryPort.generateSummary(
                            target.childId(),
                            target.startedAt(),
                            target.endedAt());

            if (gameSources.isEmpty() && conversationSummary.isEmpty()) {
                throw new IllegalStateException(
                        "A daily report requires game or conversation data.");
            }

            return persistenceService.persist(
                    reportId,
                    gameSources,
                    conversationSummary);
        } catch (RuntimeException exception) {
            markFailed(reportId, exception);
            throw exception;
        }
    }

    private <T> T requiredPort(
            ObjectProvider<T> provider,
            String message
    ) {
        T port = provider.getIfAvailable();
        if (port == null) {
            throw new IllegalStateException(message);
        }
        return port;
    }

    private void markFailed(
            Long reportId,
            RuntimeException originalException
    ) {
        try {
            statusService.markFailed(reportId, originalException);
        } catch (RuntimeException failureStatusException) {
            originalException.addSuppressed(failureStatusException);
        }
    }
}
