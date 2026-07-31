package com.A501.backend.domain.report.service;

import org.springframework.stereotype.Component;

import com.A501.backend.domain.report.entity.ReportConversationSummary;
import com.A501.backend.domain.report.model.ReportConversationSummarySource;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class ReportConversationSummaryMapper {

    private final ObjectMapper objectMapper;

    public ReportConversationSummary toSnapshot(
            Long reportId,
            ReportConversationSummarySource source
    ) {
        try {
            return ReportConversationSummary.snapshot(
                    reportId,
                    source,
                    objectMapper.writeValueAsString(source.conversationIds()),
                    objectMapper.writeValueAsString(source.mainEvents()),
                    objectMapper.writeValueAsString(source.emotions()),
                    objectMapper.writeValueAsString(source.interests()),
                    objectMapper.writeValueAsString(source.emotionDetails()),
                    objectMapper.writeValueAsString(source.notableQuotes()),
                    objectMapper.writeValueAsString(source.conversationGuide()));
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Failed to serialize the conversation summary.", exception);
        }
    }
}
