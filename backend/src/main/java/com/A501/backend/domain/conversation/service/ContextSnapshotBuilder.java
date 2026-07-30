package com.A501.backend.domain.conversation.service;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.A501.backend.domain.conversation.dto.internal.DialogueSlotsPayload;
import com.A501.backend.domain.conversation.dto.internal.DialogueTopicContextPayload;
import com.A501.backend.domain.conversation.dto.realtime.ContextSnapshot;
import com.A501.backend.domain.conversation.dto.realtime.RealtimeConversationContext;
import com.A501.backend.domain.conversation.dto.realtime.RealtimePolicySnapshot;
import com.A501.backend.domain.conversation.dto.realtime.RealtimeTopicSnapshot;
import com.A501.backend.domain.conversation.entity.ConversationGoal;
import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationStatus;
import com.A501.backend.domain.conversation.entity.ConversationTopicState;
import com.A501.backend.domain.conversation.entity.ConversationTurn;
import com.A501.backend.domain.conversation.entity.Speaker;
import com.A501.backend.domain.conversation.entity.TurnStatus;
import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;
import com.A501.backend.domain.conversation.repository.ConversationTopicStateRepository;
import com.A501.backend.domain.conversation.repository.ConversationTurnRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * AI 대화용 Context Snapshot을 조립한다. Realtime Session 발급마다 호출되며, 매 턴 Spring
 * API를 다시 호출하지 않아도 되도록(ADR-008) 그 시점의 주제·슬롯·게임 상태를 한 번에 담는다.
 */
@Component
@RequiredArgsConstructor
public class ContextSnapshotBuilder {

    private static final String DEFAULT_LANGUAGE = "ko-KR";
    private static final Set<ConversationStatus> TERMINAL_STATUSES = EnumSet.of(
            ConversationStatus.COMPLETED,
            ConversationStatus.ABORTED,
            ConversationStatus.EXPIRED,
            ConversationStatus.FAILED);
    private static final int RECENT_CONVERSATION_SUMMARY_MAX_LENGTH = 300;

    private final ConversationSessionRepository conversationSessionRepository;
    private final ConversationTopicStateRepository topicStateRepository;
    private final ConversationTurnRepository conversationTurnRepository;
    private final RealtimeChildProfileProvider childProfileProvider;
    private final ObjectMapper objectMapper;

    public ContextSnapshot build(ConversationSession session) {
        List<ConversationTopicState> topicStates = topicStateRepository
                .findAllByConversationIdOrderByTopicOrderAsc(session.getConversationId());

        List<RealtimeTopicSnapshot> topics = topicStates.stream()
                .map(topic -> toTopicSnapshot(session.getConversationId(), topic))
                .toList();
        int totalMaxQuestionCount = topicStates.stream()
                .mapToInt(ConversationTopicState::getMaxQuestionCount)
                .sum();
        int lastTurnSeq = conversationTurnRepository
                .findMaxTurnSeqByConversationId(session.getConversationId());

        return ContextSnapshot.create(
                session.getConversationId(),
                childProfileProvider.getProfile(session.getChildId()),
                new RealtimeConversationContext(
                        ConversationGoal.DIARY,
                        DEFAULT_LANGUAGE,
                        session.getCurrentTopic(),
                        totalMaxQuestionCount,
                        session.getQuestionCount(),
                        lastTurnSeq),
                buildRecentConversationSummary(session),
                topics,
                RealtimePolicySnapshot.defaultPolicy());
    }

    /**
     * 아이 프로필에는 이름만 있고 연령대·관심사 같은 별도 속성이 없으므로, 대신 그 아이의
     * 가장 최근에 종료된 대화에서 실제로 했던 답변을 짧게 참고 정보로 담는다.
     */
    private String buildRecentConversationSummary(ConversationSession session) {
        return conversationSessionRepository
                .findFirstByChildIdAndStatusInOrderByCreatedAtDesc(
                        session.getChildId(), TERMINAL_STATUSES)
                .map(previous -> summarizeRecentChildTurns(previous.getConversationId()))
                .orElse(null);
    }

    private String summarizeRecentChildTurns(String conversationId) {
        List<ConversationTurn> recentChildTurns = conversationTurnRepository
                .findTop5ByConversationIdAndSpeakerAndStatusOrderByTurnSeqDesc(
                        conversationId, Speaker.CHILD, TurnStatus.COMPLETED);
        if (recentChildTurns.isEmpty()) {
            return null;
        }

        String joined = recentChildTurns.stream()
                .sorted(Comparator.comparingInt(ConversationTurn::getTurnSeq))
                .map(ConversationTurn::getContent)
                .collect(Collectors.joining(" "));
        return joined.length() > RECENT_CONVERSATION_SUMMARY_MAX_LENGTH
                ? joined.substring(0, RECENT_CONVERSATION_SUMMARY_MAX_LENGTH)
                : joined;
    }

    private RealtimeTopicSnapshot toTopicSnapshot(
            String conversationId,
            ConversationTopicState topicState) {
        DialogueSlotsPayload slots = deserialize(
                topicState.getSlotsJson(), DialogueSlotsPayload.class);
        DialogueTopicContextPayload context = deserialize(
                topicState.getContextJson(), DialogueTopicContextPayload.class);
        List<ConversationTurn> completedTurns = conversationTurnRepository
                .findByConversationIdAndTopicTypeAndStatusOrderByTurnSeqAsc(
                        conversationId,
                        topicState.getTopicType(),
                        TurnStatus.COMPLETED);
        ConversationTurn currentAssistant =
                DialogueTopicHistoryAssembler.currentAssistantTurn(completedTurns);

        return new RealtimeTopicSnapshot(
                topicState.getTopicType(),
                topicState.getTopicOrder(),
                topicState.getMaxQuestionCount(),
                topicState.getQuestionCount(),
                context.games(),
                DialogueTopicHistoryAssembler.completedExchanges(completedTurns),
                currentAssistant == null ? null : currentAssistant.getContent(),
                currentAssistant == null
                        ? null
                        : DialogueTopicHistoryAssembler.resolveQuestionText(
                                currentAssistant),
                currentAssistant == null
                        ? null
                        : DialogueTopicHistoryAssembler.resolveSupportedQuestionTarget(
                                currentAssistant),
                deserializeSkippedTargets(topicState.getSkippedTargetsJson()),
                slots);
    }

    private List<String> deserializeSkippedTargets(String json) {
        try {
            return DialogueTopicHistoryAssembler.normalizeSupportedQuestionTargets(
                    List.of(objectMapper.readValue(json, String[].class)));
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new ConversationException(ConversationErrorCode.DIALOGUE_STATE_INVALID);
        }
    }

    private <T> T deserialize(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JacksonException exception) {
            throw new ConversationException(ConversationErrorCode.DIALOGUE_STATE_INVALID);
        }
    }
}
