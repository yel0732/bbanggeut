package com.A501.backend.domain.conversation.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationStatus;
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
    private final ConversationContentPurger conversationContentPurger;
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
        } catch (IllegalStateException exception) {
            throw new ConversationException(ConversationErrorCode.INVALID_SESSION_STATE);
        }
        ConversationSession terminated = conversationSessionRepository.saveAndFlush(session);

        // 완주(COMPLETED)한 대화만 원문을 남긴다 — 일기·리포트의 유일한 재료다.
        // 그 밖의 종료는 사유와 무관하게 대화 내용을 정리한다.
        if (terminated.getStatus() != ConversationStatus.COMPLETED) {
            conversationContentPurger.purge(conversationId);
            return terminated;
        }

        try {
            topicStateRepository
                    .findByConversationIdAndTopicTypeForUpdate(
                            conversationId,
                            terminated.getCurrentTopic())
                    .ifPresent(topic -> topic.complete(completedAt, reason));
        } catch (IllegalStateException exception) {
            throw new ConversationException(ConversationErrorCode.INVALID_SESSION_STATE);
        }

        return terminated;
    }
}
