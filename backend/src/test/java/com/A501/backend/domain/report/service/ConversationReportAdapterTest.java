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
import com.A501.backend.domain.conversation.repository.ConversationTurnRepository;
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
                mock(ConversationTurnRepository.class),
                new ObjectMapper(),
                mock(ReportAiSummaryPort.class),
                mock(GameReportQueryPort.class),
                new ReportAiProperties(
                        false,
                        "http://127.0.0.1:8000",
                        java.time.Duration.ofSeconds(1)),
                new ConversationReportTextSanitizer());

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
                mock(ConversationTurnRepository.class),
                new ObjectMapper(),
                aiPort,
                gamePort,
                new ReportAiProperties(
                        true,
                        "http://127.0.0.1:8000",
                        java.time.Duration.ofSeconds(1)),
                new ConversationReportTextSanitizer());

        var summary = adapter.generateSummary(15L, STARTED_AT, ENDED_AT)
                .orElseThrow();

        assertThat(summary.modelVersion())
                .isEqualTo("validated-dialogue-slots-v1");
        assertThat(summary.promptVersion())
                .isEqualTo("deterministic-report-v1");
        assertThat(summary.diaryContent()).contains("그림 그리기", "즐거웠다");
    }

    @Test
    void ruleBasedSummaryJoinsSlotsWithCorrectParticlesAndEndings() {
        // 조각 검사로는 "기분은 즐거웠어했다" 같은 비문을 못 잡는다. 조립 결과 전체를 본다.
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
                {"event":"블록 쌓기","people":["동생"],"place":"집",
                 "emotion":"즐거웠어","emotionReason":null,
                 "memorableMoment":"탑이 무너진 순간"}
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
                mock(ConversationTurnRepository.class),
                new ObjectMapper(),
                aiPort,
                gamePort,
                new ReportAiProperties(
                        true,
                        "http://127.0.0.1:8000",
                        java.time.Duration.ofSeconds(1)),
                new ConversationReportTextSanitizer());

        var summary = adapter.generateSummary(15L, STARTED_AT, ENDED_AT)
                .orElseThrow();

        assertThat(summary.diaryContent()).isEqualTo(
                "블록 쌓기. 동생과 함께했다. 장소는 집이었다. 기분은 즐거웠다."
                        + " 기억에 남는 순간은 탑이 무너진 순간이었다.");
    }

    @Test
    void keepsUnsafeConversationTextOutOfTheReportAndGuidesTheParent() {
        ConversationSessionRepository sessionRepository =
                mock(ConversationSessionRepository.class);
        ConversationTopicStateRepository topicRepository =
                mock(ConversationTopicStateRepository.class);
        ConversationTurnRepository turnRepository =
                mock(ConversationTurnRepository.class);
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
                {"event":"블록 놀이","people":[],"place":null,
                 "emotion":"슬펐다","emotionReason":"블록이 망가져서",
                 "memorableMoment":null}
                """);
        when(topicRepository
                .findAllByConversationIdInAndTopicTypeOrderByCompletedAtAsc(
                        List.of("conversation-1"),
                        ConversationTopic.DAILY))
                .thenReturn(List.of(topic));
        when(turnRepository
                .findAllByConversationIdInAndTopicTypeAndStatusOrderByConversationIdAscTurnSeqAsc(
                        List.of("conversation-1"),
                        ConversationTopic.DAILY,
                        com.A501.backend.domain.conversation.entity.TurnStatus.COMPLETED))
                .thenReturn(List.of(
                        com.A501.backend.domain.conversation.entity.ConversationTurn.createChildCompleted(
                                "turn-1",
                                "conversation-1",
                                1,
                                "죽고 싶어",
                                null,
                                STARTED_AT,
                                STARTED_AT.plusSeconds(1)),
                        com.A501.backend.domain.conversation.entity.ConversationTurn.createChildCompleted(
                                "turn-2",
                                "conversation-1",
                                2,
                                "우리 유치원 선생님이랑 블록 놀이 했어",
                                null,
                                STARTED_AT.plusSeconds(2),
                                STARTED_AT.plusSeconds(3))));
        ConversationReportAdapter adapter = new ConversationReportAdapter(
                sessionRepository,
                topicRepository,
                turnRepository,
                new ObjectMapper(),
                mock(ReportAiSummaryPort.class),
                mock(GameReportQueryPort.class),
                new ReportAiProperties(
                        false,
                        "http://127.0.0.1:8000",
                        java.time.Duration.ofSeconds(1)),
                new ConversationReportTextSanitizer());

        var summary = adapter.generateSummary(15L, STARTED_AT, ENDED_AT)
                .orElseThrow();

        // 안전 신호가 담긴 발화만 빠지고, 유치원·선생님이 나오는 일상 발화는 그대로 쓴다.
        assertThat(summary.notableQuotes())
                .containsExactly("우리 유치원 선생님이랑 블록 놀이 했어");
        assertThat(summary.conversationGuide().description())
                .contains("편안히 이야기해볼 필요");
        assertThat(summary.conversationGuide().questions())
                .anyMatch(question -> question.contains("마음이 많이 힘들었던 일"));
    }
}
