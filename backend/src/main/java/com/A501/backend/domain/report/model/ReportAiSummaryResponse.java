package com.A501.backend.domain.report.model;

import java.util.List;

public record ReportAiSummaryResponse(
        String title,
        String dailyStory,
        List<ReportEmotionDetail> emotionDetails,
        List<String> interests,
        List<String> notableQuotes,
        ReportConversationGuide conversationGuide,
        String modelVersion,
        String promptVersion
) {
}
