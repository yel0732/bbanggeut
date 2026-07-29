package com.A501.backend.domain.conversation.service;

import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.A501.backend.domain.conversation.dto.stream.ConversationStreamEvent;
import com.A501.backend.domain.conversation.dto.stream.ConversationStreamEventType;
import com.A501.backend.domain.conversation.dto.stream.SessionCompletedPayload;
import com.A501.backend.domain.conversation.dto.stream.SessionFailedPayload;
import com.A501.backend.domain.conversation.dto.stream.SessionStartedPayload;
import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationStatus;
import com.A501.backend.domain.conversation.entity.ConversationTopicState;
import com.A501.backend.domain.conversation.entity.ConversationTopicStatus;
import com.A501.backend.domain.conversation.entity.TerminationReason;
import com.A501.backend.domain.conversation.repository.ConversationTopicStateRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ConversationSessionEventHandler implements ConversationEventHandler {

    private final ConversationEventPayloadReader payloadReader;
    private final ConversationTopicStateRepository topicStateRepository;
    private final ConversationContentPurger conversationContentPurger;

    @Override
    public Set<ConversationStreamEventType> supportedTypes() {
        return Set.of(
                ConversationStreamEventType.SESSION_STARTED,
                ConversationStreamEventType.SESSION_COMPLETED,
                ConversationStreamEventType.SESSION_FAILED);
    }

    @Override
    public void handle(ConversationStreamEvent event, ConversationSession session) {
        switch (event.eventType()) {
            case SESSION_STARTED -> start(event, session);
            case SESSION_COMPLETED -> complete(event, session);
            case SESSION_FAILED -> fail(event, session);
            default -> throw new ConversationEventContractException(
                    "Unsupported session event type: " + event.eventType());
        }
    }

    private void start(ConversationStreamEvent event, ConversationSession session) {
        SessionStartedPayload payload =
                payloadReader.read(event, SessionStartedPayload.class);
        LocalDateTime connectedAt =
                payloadReader.toUtc(payload.connectedAt(), "payload.connectedAt");
        if (session.getStatus() == ConversationStatus.READY) {
            session.start(connectedAt);
        }
        ConversationTopicState topicState = findCurrentTopicForUpdate(session);
        if (topicState.getStatus() == ConversationTopicStatus.PENDING) {
            topicState.start(connectedAt);
        }
    }

    private void complete(ConversationStreamEvent event, ConversationSession session) {
        SessionCompletedPayload payload =
                payloadReader.read(event, SessionCompletedPayload.class);
        if (session.getStatus().isTerminal()) {
            return;
        }
        LocalDateTime completedAt =
                payloadReader.toUtc(payload.completedAt(), "payload.completedAt");
        try {
            TerminationReason reason = TerminationReason.from(payload.reason());
            // 완주가 아닌 종료(아이의 그만하기·무응답 타임아웃 등)는 대화 원문을
            // 남기지 않는다. 정리 대상 주제를 굳이 완료 처리하지 않는다.
            if (reason.getTargetStatus() == ConversationStatus.COMPLETED) {
                findCurrentTopicForUpdate(session).complete(completedAt, reason);
                session.terminate(completedAt, reason);
            } else {
                session.terminate(completedAt, reason);
                conversationContentPurger.purge(session.getConversationId());
            }
        } catch (IllegalArgumentException exception) {
            throw new ConversationEventContractException(
                    "payload.reason is invalid.",
                    exception);
        }
    }

    private void fail(ConversationStreamEvent event, ConversationSession session) {
        SessionFailedPayload payload =
                payloadReader.read(event, SessionFailedPayload.class);
        if (session.getStatus().isTerminal()) {
            return;
        }
        LocalDateTime failedAt =
                payloadReader.toUtc(payload.failedAt(), "payload.failedAt");
        session.terminate(failedAt, TerminationReason.SYSTEM_ERROR);
        conversationContentPurger.purge(session.getConversationId());
    }

    private ConversationTopicState findCurrentTopicForUpdate(
            ConversationSession session) {
        return topicStateRepository
                .findByConversationIdAndTopicTypeForUpdate(
                        session.getConversationId(),
                        session.getCurrentTopic())
                .orElseThrow(() -> new ConversationEventContractException(
                        "Conversation topic state does not exist."));
    }
}
