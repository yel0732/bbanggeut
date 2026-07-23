package com.A501.backend.domain.report.port;

import java.time.LocalDateTime;
import java.util.Optional;

import com.A501.backend.domain.report.model.ReportConversationSummarySource;

public interface ConversationSummaryPort {

    Optional<ReportConversationSummarySource> generateSummary(
            Long childId,
            LocalDateTime startedAt,
            LocalDateTime endedAt);
}
