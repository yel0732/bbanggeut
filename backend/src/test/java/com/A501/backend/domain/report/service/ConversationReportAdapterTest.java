package com.A501.backend.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationStatus;
import com.A501.backend.domain.conversation.entity.ConversationTopic;
import com.A501.backend.domain.conversation.entity.ConversationTopicState;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;
import com.A501.backend.domain.conversation.repository.ConversationTopicStateRepository;
import com.A501.backend.domain.report.model.ReportAiProperties;
import com.A501.backend.domain.report.port.GameReportQueryPort;
import com.A501.backend.domain.report.port.ReportAiSummaryPort;

import tools.jackson.databind.ObjectMapper;

class ConversationReportAdapterTest {

    private static final LocalDateTime STARTED_AT =
            LocalDateTime.of(2026, 7, 22, 19, 0);
    private static final LocalDateTime ENDED_AT =
            LocalDateTime.of(2026, 7, 23, 19, 0);

    @Test
    void buildsSummaryOnlyFromValidatedDiarySlots() {
        ConversationSessionRepository sessionRepository =
                mock(ConversationSessionRepository.class);
        ConversationTopicStateRepository topicRepository =
                mock(ConversationTopicStateRepository.class);
        ConversationSession session = mock(ConversationSession.class);
        when(session.getConversationId()).thenReturn("conversation-1");
        when(sessionRepository
                .findAllByChildIdAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAtAsc(
                        15L,
                        ConversationStatus.COMPLETED,
                        STARTED_AT,
                        ENDED_AT))
                .thenReturn(List.of(session));
        ConversationTopicState topic = mock(ConversationTopicState.class);
        when(topic.getConversationId()).thenReturn("conversation-1");
        when(topic.getSlotsJson()).thenReturn("""
                {"event":"친구와 공놀이","people":["민수"],"place":"놀이터",
                 "emotion":"즐거웠다","emotionReason":"함께 뛰어서",
                 "memorableMoment":"골을 넣은 순간"}
                """);
        when(topicRepository
                .findAllByConversationIdInAndTopicTypeOrderByCompletedAtAsc(
                        List.of("conversation-1"),
                        ConversationTopic.DAILY))
                .thenReturn(List.of(topic));
        ConversationReportAdapter adapter = new ConversationReportAdapter(
                sessionRepository,
                topicRepository,
                new ObjectMapper(),
                mock(ReportAiSummaryPort.class),
                mock(GameReportQueryPort.class),
                new ReportAiProperties(
                        false,
                        "http://127.0.0.1:8000",
                        java.time.Duration.ofSeconds(1)));

        var summary = adapter.generateSummary(15L, STARTED_AT, ENDED_AT)
                .orElseThrow();

        assertThat(summary.conversationIds()).containsExactly("conversation-1");
        assertThat(summary.diaryTitle()).contains("친구와 공놀이");
        assertThat(summary.diaryContent())
                .contains("민수", "놀이터", "즐거웠다", "골을 넣은 순간");
        assertThat(summary.interests()).isEmpty();
    }

    @Test
    void usesRuleBasedSummaryWhenReportAiIsUnavailable() {
        ConversationSessionRepository sessionRepository =
                mock(ConversationSessionRepository.class);
        ConversationTopicStateRepository topicRepository =
                mock(ConversationTopicStateRepository.class);
        ConversationSession session = mock(ConversationSession.class);
        when(session.getConversationId()).thenReturn("conversation-1");
        when(sessionRepository
                .findAllByChildIdAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAtAsc(
                        15L,
                        ConversationStatus.COMPLETED,
                        STARTED_AT,
                        ENDED_AT))
                .thenReturn(List.of(session));
        ConversationTopicState topic = mock(ConversationTopicState.class);
        when(topic.getConversationId()).thenReturn("conversation-1");
        when(topic.getSlotsJson()).thenReturn("""
                {"event":"그림 그리기","people":[],"place":null,
                 "emotion":"즐거웠다","emotionReason":null,
                 "memorableMoment":null}
                """);
        when(topicRepository
                .findAllByConversationIdInAndTopicTypeOrderByCompletedAtAsc(
                        List.of("conversation-1"),
                        ConversationTopic.DAILY))
                .thenReturn(List.of(topic));
        ReportAiSummaryPort aiPort = mock(ReportAiSummaryPort.class);
        when(aiPort.generate(any())).thenThrow(
                new ReportAiUnavailableException("AI unavailable"));
        GameReportQueryPort gamePort = mock(GameReportQueryPort.class);
        when(gamePort.findCompletedGames(15L, STARTED_AT, ENDED_AT))
                .thenReturn(List.of());
        ConversationReportAdapter adapter = new ConversationReportAdapter(
                sessionRepository,
                topicRepository,
                new ObjectMapper(),
                aiPort,
                gamePort,
                new ReportAiProperties(
                        true,
                        "http://127.0.0.1:8000",
                        java.time.Duration.ofSeconds(1)));

        var summary = adapter.generateSummary(15L, STARTED_AT, ENDED_AT)
                .orElseThrow();

        assertThat(summary.modelVersion())
                .isEqualTo("validated-dialogue-slots-v1");
        assertThat(summary.promptVersion())
                .isEqualTo("deterministic-report-v1");
        assertThat(summary.diaryContent()).contains("그림 그리기", "즐거웠다");
    }
}
