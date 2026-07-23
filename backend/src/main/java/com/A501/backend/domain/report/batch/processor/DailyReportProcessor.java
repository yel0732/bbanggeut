package com.A501.backend.domain.report.batch.processor;

import org.springframework.batch.infrastructure.item.ItemProcessor;

import com.A501.backend.domain.report.model.DailyReportCandidate;
import com.A501.backend.domain.report.model.ReportPeriod;
import com.A501.backend.domain.report.port.ConversationReportQueryPort;
import com.A501.backend.domain.report.port.GameReportQueryPort;

public class DailyReportProcessor implements ItemProcessor<Long, DailyReportCandidate> {

    private final GameReportQueryPort gameReportQueryPort;
    private final ConversationReportQueryPort conversationReportQueryPort;
    private final ReportPeriod period;

    public DailyReportProcessor(
            GameReportQueryPort gameReportQueryPort,
            ConversationReportQueryPort conversationReportQueryPort,
            ReportPeriod period) {
        this.gameReportQueryPort = gameReportQueryPort;
        this.conversationReportQueryPort = conversationReportQueryPort;
        this.period = period;
    }

    @Override
    public DailyReportCandidate process(Long childId) {
        boolean hasGame = gameReportQueryPort.existsCompletedGame(
                childId,
                period.startedAt(),
                period.endedAt());

        boolean hasConversation = conversationReportQueryPort.existsReportableConversation(
                childId,
                period.startedAt(),
                period.endedAt());

        if (!hasGame && !hasConversation) {
            return null;
        }

        return new DailyReportCandidate(childId, period);
    }
}
