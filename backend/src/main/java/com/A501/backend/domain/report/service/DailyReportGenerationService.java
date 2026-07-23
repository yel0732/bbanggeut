package com.A501.backend.domain.report.service;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.A501.backend.domain.report.entity.ReportErrorCode;
import com.A501.backend.domain.report.entity.ReportFailureStage;
import com.A501.backend.domain.report.model.DailyReportGenerationResult;
import com.A501.backend.domain.report.model.DailyReportGenerationTarget;
import com.A501.backend.domain.report.model.ReportConversationSummarySource;
import com.A501.backend.domain.report.model.ReportGameSource;
import com.A501.backend.domain.report.model.ReportGenerationFailure;
import com.A501.backend.domain.report.model.ReportRetryProperties;
import com.A501.backend.domain.report.port.ConversationSummaryPort;
import com.A501.backend.domain.report.port.GameReportQueryPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DailyReportGenerationService {

    private final DailyReportStatusService statusService;
    private final DailyReportPersistenceService persistenceService;
    private final ReportGenerationAttemptService attemptService;
    private final ReportManualRegenerationHistoryService
            manualRegenerationHistoryService;
    private final ReportNotificationPublisher notificationPublisher;
    private final ReportRetryWaiter retryWaiter;
    private final ReportRetryProperties retryProperties;
    private final ObjectProvider<GameReportQueryPort> gameReportQueryPortProvider;
    private final ObjectProvider<ConversationSummaryPort> conversationSummaryPortProvider;

    public DailyReportGenerationResult generate(Long reportId) {
        manualRegenerationHistoryService.markProcessing(reportId);
        try {
            DailyReportGenerationResult result = generateWithRetry(reportId);
            notificationPublisher.publishCompleted(reportId);
            manualRegenerationHistoryService.markCompleted(reportId);
            return result;
        } catch (RuntimeException exception) {
            manualRegenerationHistoryService.markFailed(reportId, exception);
            throw exception;
        }
    }

    private DailyReportGenerationResult generateWithRetry(Long reportId) {
        DailyReportGenerationTarget target =
                statusService.startProcessing(reportId);

        for (int attemptIndex = 0;
                attemptIndex < retryProperties.maxAttempts();
                attemptIndex++) {
            Long attemptId = startAttemptOrFail(reportId);

            try {
                return generateAttempt(target, attemptId);
            } catch (ReportGenerationFailure failure) {
                if (!recordAttemptFailure(attemptId, failure)) {
                    markFinalFailure(reportId, failure.originalException());
                    throw failure.originalException();
                }

                boolean lastAttempt =
                        attemptIndex + 1 >= retryProperties.maxAttempts();
                if (!failure.retryable() || lastAttempt) {
                    markFinalFailure(reportId, failure.originalException());
                    throw failure.originalException();
                }

                waitAndRecordRetryOrFail(reportId);
            }
        }

        throw new IllegalStateException("Report retry loop ended unexpectedly.");
    }

    private DailyReportGenerationResult generateAttempt(
            DailyReportGenerationTarget target,
            Long attemptId
    ) {
        GameReportQueryPort gameReportQueryPort = executeStage(
                ReportFailureStage.PORT_CONNECTION,
                ReportErrorCode.REPORT_PORT_NOT_CONNECTED,
                false,
                () -> requiredPort(
                        gameReportQueryPortProvider,
                        "GameReportQueryPort is not connected."));
        ConversationSummaryPort conversationSummaryPort = executeStage(
                ReportFailureStage.PORT_CONNECTION,
                ReportErrorCode.REPORT_PORT_NOT_CONNECTED,
                false,
                () -> requiredPort(
                        conversationSummaryPortProvider,
                        "ConversationSummaryPort is not connected."));

        List<ReportGameSource> gameSources = executeStage(
                ReportFailureStage.GAME_QUERY,
                ReportErrorCode.GAME_QUERY_FAILED,
                true,
                () -> gameReportQueryPort.findCompletedGames(
                        target.childId(),
                        target.startedAt(),
                        target.endedAt()));
        Optional<ReportConversationSummarySource> conversationSummary =
                executeStage(
                        ReportFailureStage.CONVERSATION_SUMMARY,
                        ReportErrorCode.CONVERSATION_SUMMARY_FAILED,
                        true,
                        () -> conversationSummaryPort.generateSummary(
                                target.childId(),
                                target.startedAt(),
                                target.endedAt()));

        if (gameSources.isEmpty() && conversationSummary.isEmpty()) {
            throw failure(
                    ReportFailureStage.SOURCE_VALIDATION,
                    ReportErrorCode.REPORT_SOURCE_EMPTY,
                    false,
                    new IllegalStateException(
                            "A daily report requires game or conversation data."));
        }

        try {
            return persistenceService.persist(
                    target.reportId(),
                    attemptId,
                    gameSources,
                    conversationSummary);
        } catch (IllegalArgumentException exception) {
            throw failure(
                    ReportFailureStage.SNAPSHOT_PERSISTENCE,
                    ReportErrorCode.SNAPSHOT_VALIDATION_FAILED,
                    false,
                    exception);
        } catch (RuntimeException exception) {
            throw failure(
                    ReportFailureStage.SNAPSHOT_PERSISTENCE,
                    ReportErrorCode.SNAPSHOT_PERSISTENCE_FAILED,
                    true,
                    exception);
        }
    }

    private Long startAttemptOrFail(Long reportId) {
        try {
            return attemptService.startAttempt(reportId);
        } catch (RuntimeException exception) {
            markFinalFailure(reportId, exception);
            throw exception;
        }
    }

    private boolean recordAttemptFailure(
            Long attemptId,
            ReportGenerationFailure failure
    ) {
        try {
            attemptService.markFailed(attemptId, failure);
            return true;
        } catch (RuntimeException attemptHistoryException) {
            failure.originalException().addSuppressed(attemptHistoryException);
            return false;
        }
    }

    private void waitAndRecordRetryOrFail(Long reportId) {
        try {
            retryWaiter.waitForNextAttempt();
            statusService.recordRetry(reportId);
        } catch (RuntimeException exception) {
            markFinalFailure(reportId, exception);
            throw exception;
        }
    }

    private <T> T executeStage(
            ReportFailureStage stage,
            ReportErrorCode errorCode,
            boolean retryable,
            Supplier<T> operation
    ) {
        try {
            return operation.get();
        } catch (ReportGenerationFailure failure) {
            throw failure;
        } catch (RuntimeException exception) {
            throw failure(stage, errorCode, retryable, exception);
        }
    }

    private ReportGenerationFailure failure(
            ReportFailureStage stage,
            ReportErrorCode errorCode,
            boolean retryable,
            RuntimeException exception
    ) {
        return new ReportGenerationFailure(
                stage,
                errorCode,
                retryable,
                exception);
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

    private void markFinalFailure(
            Long reportId,
            RuntimeException originalException
    ) {
        try {
            statusService.markFailed(reportId, originalException);
            notificationPublisher.publishFinalFailure(reportId);
        } catch (RuntimeException failureStatusException) {
            originalException.addSuppressed(failureStatusException);
        }
    }
}
