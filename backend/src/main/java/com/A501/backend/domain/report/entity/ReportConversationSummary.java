package com.A501.backend.domain.report.entity;

import com.A501.backend.common.entity.BaseTimeEntity;
import com.A501.backend.domain.report.model.ReportConversationSummarySource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "report_conversation_summary",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_report_conversation_summary_report",
                        columnNames = "report_id")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportConversationSummary extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_conversation_summary_id")
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "source_conversation_ids", nullable = false, columnDefinition = "TEXT")
    private String sourceConversationIds;

    @Column(name = "diary_title", nullable = false, length = 200)
    private String diaryTitle;

    @Column(name = "diary_content", nullable = false, columnDefinition = "TEXT")
    private String diaryContent;

    @Column(name = "main_events", nullable = false, columnDefinition = "TEXT")
    private String mainEvents;

    @Column(name = "emotions", nullable = false, columnDefinition = "TEXT")
    private String emotions;

    @Column(name = "emotion_details", nullable = false, columnDefinition = "TEXT")
    private String emotionDetails;

    @Column(name = "interests", nullable = false, columnDefinition = "TEXT")
    private String interests;

    @Column(name = "notable_quotes", nullable = false, columnDefinition = "TEXT")
    private String notableQuotes;

    @Column(name = "conversation_guide", nullable = false, columnDefinition = "TEXT")
    private String conversationGuide;

    @Column(name = "model_version", length = 100)
    private String modelVersion;

    @Column(name = "prompt_version", length = 100)
    private String promptVersion;

    @Builder(access = AccessLevel.PRIVATE)
    private ReportConversationSummary(
            Long reportId,
            String sourceConversationIds,
            String diaryTitle,
            String diaryContent,
            String mainEvents,
            String emotions,
            String emotionDetails,
            String interests,
            String notableQuotes,
            String conversationGuide,
            String modelVersion,
            String promptVersion
    ) {
        this.reportId = reportId;
        this.sourceConversationIds = sourceConversationIds;
        this.diaryTitle = diaryTitle;
        this.diaryContent = diaryContent;
        this.mainEvents = mainEvents;
        this.emotions = emotions;
        this.emotionDetails = emotionDetails;
        this.interests = interests;
        this.notableQuotes = notableQuotes;
        this.conversationGuide = conversationGuide;
        this.modelVersion = modelVersion;
        this.promptVersion = promptVersion;
    }

    public static ReportConversationSummary snapshot(
            Long reportId,
            ReportConversationSummarySource source,
            String sourceConversationIds,
            String mainEvents,
            String emotions,
            String interests,
            String emotionDetails,
            String notableQuotes,
            String conversationGuide
    ) {
        validate(reportId, source);

        return ReportConversationSummary.builder()
                .reportId(reportId)
                .sourceConversationIds(sourceConversationIds)
                .diaryTitle(source.diaryTitle())
                .diaryContent(source.diaryContent())
                .mainEvents(mainEvents)
                .emotions(emotions)
                .emotionDetails(emotionDetails)
                .interests(interests)
                .notableQuotes(notableQuotes)
                .conversationGuide(conversationGuide)
                .modelVersion(source.modelVersion())
                .promptVersion(source.promptVersion())
                .build();
    }

    public static ReportConversationSummary snapshot(
            Long reportId,
            ReportConversationSummarySource source,
            String sourceConversationIds,
            String mainEvents,
            String emotions,
            String interests) {
        return snapshot(
                reportId,
                source,
                sourceConversationIds,
                mainEvents,
                emotions,
                interests,
                "[]",
                "[]",
                "{\"description\":\"\",\"questions\":[]}");
    }

    private static void validate(
            Long reportId,
            ReportConversationSummarySource source
    ) {
        if (reportId == null) {
            throw new IllegalArgumentException("Report ID is required.");
        }
        if (source == null) {
            throw new IllegalArgumentException("Conversation summary source is required.");
        }
        if (source.diaryTitle() == null || source.diaryTitle().isBlank()) {
            throw new IllegalArgumentException("Diary title is required.");
        }
        if (source.diaryContent() == null || source.diaryContent().isBlank()) {
            throw new IllegalArgumentException("Diary content is required.");
        }
    }
}
