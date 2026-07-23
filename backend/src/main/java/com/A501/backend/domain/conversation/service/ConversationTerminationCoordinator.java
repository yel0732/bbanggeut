package com.A501.backend.domain.conversation.service;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.TerminationReason;
import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConversationTerminationCoordinator {

    private static final int MAX_TERMINATION_ATTEMPTS = 2;

    private final ConversationSessionRepository conversationSessionRepository;
    private final ConversationTerminationExecutor conversationTerminationExecutor;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ConversationSession terminate(String conversationId, TerminationReason reason) {
        for (int attempt = 0; attempt < MAX_TERMINATION_ATTEMPTS; attempt++) {
            try {
                return conversationTerminationExecutor.terminate(conversationId, reason);
            } catch (ObjectOptimisticLockingFailureException exception) {
                ConversationSession latest = findConversation(conversationId);
                if (latest.getStatus().isTerminal()) {
                    return latest;
                }
                if (attempt == MAX_TERMINATION_ATTEMPTS - 1) {
                    throw new ConversationException(ConversationErrorCode.INVALID_SESSION_STATE);
                }
            }
        }

        throw new ConversationException(ConversationErrorCode.INVALID_SESSION_STATE);
    }

    private ConversationSession findConversation(String conversationId) {
        return conversationSessionRepository.findById(conversationId)
                .orElseThrow(() -> new ConversationException(ConversationErrorCode.CONVERSATION_NOT_FOUND));
    }
}
