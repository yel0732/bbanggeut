package com.A501.backend.domain.conversation.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.TerminationReason;
import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;
import com.A501.backend.domain.conversation.repository.ConversationTopicStateRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ConversationTerminationExecutor {

    private final ConversationSessionRepository conversationSessionRepository;
    private final ConversationTopicStateRepository topicStateRepository;
    private final Clock utcClock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConversationSession terminate(String conversationId, TerminationReason reason) {
        ConversationSession session = conversationSessionRepository.findById(conversationId)
                .orElseThrow(() -> new ConversationException(ConversationErrorCode.CONVERSATION_NOT_FOUND));

        if (session.getStatus().isTerminal()) {
            return session;
        }

        LocalDateTime completedAt =
                LocalDateTime.now(utcClock).truncatedTo(ChronoUnit.MICROS);
        try {
            session.terminate(completedAt, reason);
            topicStateRepository
                    .findByConversationIdAndTopicTypeForUpdate(
                            conversationId,
                            session.getCurrentTopic())
                    .ifPresent(topic -> topic.complete(completedAt, reason));
        } catch (IllegalStateException exception) {
            throw new ConversationException(ConversationErrorCode.INVALID_SESSION_STATE);
        }

        return conversationSessionRepository.saveAndFlush(session);
    }
}
