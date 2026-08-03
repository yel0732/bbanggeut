package com.A501.backend.domain.report.model;

import java.time.LocalDate;
import java.util.List;

public record ReportAiSummaryRequest(
        LocalDate reportDate,
        List<String> events,
        List<String> emotions,
        List<String> interests,
        List<ReportAiConversationTurn> conversations,
        List<ReportAiGameInput> games
) {
    public ReportAiSummaryRequest {
        events = List.copyOf(events);
        emotions = List.copyOf(emotions);
        interests = List.copyOf(interests);
        conversations = List.copyOf(conversations);
        games = List.copyOf(games);
    }
}
