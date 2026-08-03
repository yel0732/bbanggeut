package com.A501.backend.domain.report.model;

import java.util.List;

public record ReportConversationSummarySource(
        List<String> conversationIds,
        String diaryTitle,
        String diaryContent,
        List<String> mainEvents,
        List<String> emotions,
        List<String> interests,
        List<ReportEmotionDetail> emotionDetails,
        List<String> notableQuotes,
        ReportConversationGuide conversationGuide,
        String modelVersion,
        String promptVersion
) {

    public ReportConversationSummarySource {
        conversationIds = immutableCopy(conversationIds);
        mainEvents = immutableCopy(mainEvents);
        emotions = immutableCopy(emotions);
        interests = immutableCopy(interests);
        emotionDetails = emotionDetails == null ? List.of() : List.copyOf(emotionDetails);
        notableQuotes = immutableCopy(notableQuotes);
        conversationGuide = conversationGuide == null
                ? ReportConversationGuide.empty()
                : conversationGuide;
    }

    public ReportConversationSummarySource(
            List<String> conversationIds,
            String diaryTitle,
            String diaryContent,
            List<String> mainEvents,
            List<String> emotions,
            List<String> interests,
            String modelVersion,
            String promptVersion) {
        this(
                conversationIds,
                diaryTitle,
                diaryContent,
                mainEvents,
                emotions,
                interests,
                List.of(),
                List.of(),
                ReportConversationGuide.empty(),
                modelVersion,
                promptVersion);
    }

    private static List<String> immutableCopy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
