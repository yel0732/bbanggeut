package com.A501.backend.domain.report.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.A501.backend.domain.report.entity.ReportConversationSummary;
import com.A501.backend.domain.report.model.ReportConversationGuide;
import com.A501.backend.domain.report.model.ReportEmotionDetail;

public record ReportConversationSummaryResponse(
        String diaryTitle,
        String diaryContent,
        List<String> mainEvents,
        List<String> expressedEmotions,
        List<String> interests,
        List<ReportEmotionDetail> emotionDetails,
        List<String> notableQuotes,
        ReportConversationGuide conversationGuide,
        int sourceConversationCount,
        LocalDateTime generatedAt
) {

    public static ReportConversationSummaryResponse from(
            ReportConversationSummary summary,
            List<String> sourceConversationIds,
            List<String> mainEvents,
            List<String> emotions,
            List<String> interests,
            List<ReportEmotionDetail> emotionDetails,
            List<String> notableQuotes,
            ReportConversationGuide conversationGuide
    ) {
        return new ReportConversationSummaryResponse(
                summary.getDiaryTitle(),
                summary.getDiaryContent(),
                mainEvents,
                emotions,
                interests,
                emotionDetails,
                notableQuotes,
                conversationGuide,
                sourceConversationIds.size(),
                summary.getCreatedAt());
    }
}
