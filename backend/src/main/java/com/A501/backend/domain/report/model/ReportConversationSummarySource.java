package com.A501.backend.domain.report.model;

import java.util.List;

public record ReportConversationSummarySource(
        List<String> conversationIds,
        String diaryTitle,
        String diaryContent,
        List<String> mainEvents,
        List<String> emotions,
        List<String> interests,
        String modelVersion,
        String promptVersion
) {

    public ReportConversationSummarySource {
        conversationIds = immutableCopy(conversationIds);
        mainEvents = immutableCopy(mainEvents);
        emotions = immutableCopy(emotions);
        interests = immutableCopy(interests);
    }

    private static List<String> immutableCopy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
