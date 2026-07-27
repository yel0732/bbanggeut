package com.A501.backend.domain.conversation.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.A501.backend.domain.conversation.dto.internal.DialogueSlotsPayload;
import com.A501.backend.domain.conversation.dto.internal.DialogueTopicContextPayload;
import com.A501.backend.domain.conversation.dto.realtime.ContextSnapshot;
import com.A501.backend.domain.conversation.dto.realtime.RealtimeConversationContext;
import com.A501.backend.domain.conversation.dto.realtime.RealtimePolicySnapshot;
import com.A501.backend.domain.conversation.dto.realtime.RealtimeTopicSnapshot;
import com.A501.backend.domain.conversation.entity.ConversationGoal;
import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationTopicState;
import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;
import com.A501.backend.domain.conversation.repository.ConversationTopicStateRepository;

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

    private final ConversationTopicStateRepository topicStateRepository;
    private final RealtimeChildProfileProvider childProfileProvider;
    private final ObjectMapper objectMapper;

    public ContextSnapshot build(ConversationSession session) {
        List<ConversationTopicState> topicStates = topicStateRepository
                .findAllByConversationIdOrderByTopicOrderAsc(session.getConversationId());

        List<RealtimeTopicSnapshot> topics = topicStates.stream()
                .map(this::toTopicSnapshot)
                .toList();
        int totalMaxQuestionCount = topicStates.stream()
                .mapToInt(ConversationTopicState::getMaxQuestionCount)
                .sum();

        return ContextSnapshot.create(
                session.getConversationId(),
                childProfileProvider.getProfile(session.getChildId()),
                new RealtimeConversationContext(
                        ConversationGoal.DIARY,
                        DEFAULT_LANGUAGE,
                        session.getCurrentTopic(),
                        totalMaxQuestionCount),
                null,
                topics,
                RealtimePolicySnapshot.defaultPolicy());
    }

    private RealtimeTopicSnapshot toTopicSnapshot(ConversationTopicState topicState) {
        DialogueSlotsPayload slots = deserialize(
                topicState.getSlotsJson(), DialogueSlotsPayload.class);
        DialogueTopicContextPayload context = deserialize(
                topicState.getContextJson(), DialogueTopicContextPayload.class);

        return new RealtimeTopicSnapshot(
                topicState.getTopicType(),
                topicState.getTopicOrder(),
                topicState.getMaxQuestionCount(),
                context.games(),
                slots);
    }

    private <T> T deserialize(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JacksonException exception) {
            throw new ConversationException(ConversationErrorCode.DIALOGUE_STATE_INVALID);
        }
    }
}
