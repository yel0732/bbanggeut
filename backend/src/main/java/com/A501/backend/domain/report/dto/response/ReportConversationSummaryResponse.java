package com.A501.backend.domain.report.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.A501.backend.domain.report.entity.ReportConversationSummary;

public record ReportConversationSummaryResponse(
        String diaryTitle,
        String diaryContent,
        List<String> mainEvents,
        List<String> expressedEmotions,
        List<String> interests,
        int sourceConversationCount,
        LocalDateTime generatedAt
) {

    public static ReportConversationSummaryResponse from(
            ReportConversationSummary summary,
            List<String> sourceConversationIds,
            List<String> mainEvents,
            List<String> emotions,
            List<String> interests
    ) {
        return new ReportConversationSummaryResponse(
                summary.getDiaryTitle(),
                summary.getDiaryContent(),
                mainEvents,
                emotions,
                interests,
                sourceConversationIds.size(),
                summary.getCreatedAt());
    }
}
