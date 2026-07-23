package com.A501.backend.domain.report.port;

import java.time.LocalDateTime;

public interface ConversationReportQueryPort {

    boolean existsReportableConversation(
            Long childId,
            LocalDateTime startedAt,
            LocalDateTime endedAt);
}
