package com.A501.backend.domain.conversation.service;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.conversation.dto.request.CreateConversationRequest;
import com.A501.backend.domain.conversation.dto.response.CreateConversationResponse;
import com.A501.backend.domain.conversation.dto.response.ConversationSessionResponse;
import com.A501.backend.domain.conversation.entity.ContextType;
import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationStatus;
import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;
import com.A501.backend.domain.game.service.GameSessionService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConversationSessionService {

    static final int MIN_QUESTION_COUNT = 5;
    static final int MAX_QUESTION_COUNT = 6;

    private static final Set<ConversationStatus> ACTIVE_STATUSES =
            EnumSet.of(ConversationStatus.READY, ConversationStatus.IN_PROGRESS);

    private final ConversationSessionRepository conversationSessionRepository;
    private final GameSessionService gameSessionService;
    private final ChildAccessVerifier childAccessVerifier;
    private final CurrentMemberProvider currentMemberProvider;

    @Transactional
    public CreateConversationResponse create(Long childId, CreateConversationRequest request) {
        childAccessVerifier.verifyAccess(currentMemberProvider.getCurrentMemberId(), childId);
        validateMaxQuestionCount(request.maxQuestionCount());
        validateContext(childId, request);
        validateNoActiveConversation(childId);

        ConversationSession session = ConversationSession.create(
                generateConversationId(),
                childId,
                request.gameSessionId(),
                request.contextType(),
                request.conversationGoal(),
                request.maxQuestionCount());

        return CreateConversationResponse.from(conversationSessionRepository.save(session));
    }

    public ConversationSessionResponse getConversation(String conversationId) {
        ConversationSession session = conversationSessionRepository.findById(conversationId)
                .orElseThrow(() -> new ConversationException(ConversationErrorCode.CONVERSATION_NOT_FOUND));

        verifyConversationAccess(session);
        return ConversationSessionResponse.from(session);
    }

    private void verifyConversationAccess(ConversationSession session) {
        try {
            childAccessVerifier.verifyAccess(currentMemberProvider.getCurrentMemberId(), session.getChildId());
        } catch (ConversationException exception) {
            if (exception.getErrorCode() == ConversationErrorCode.CHILD_ACCESS_DENIED) {
                throw new ConversationException(ConversationErrorCode.CONVERSATION_ACCESS_DENIED);
            }
            if (exception.getErrorCode() == ConversationErrorCode.CHILD_NOT_FOUND) {
                throw new ConversationException(ConversationErrorCode.CONVERSATION_NOT_FOUND);
            }
            throw exception;
        }
    }

    private void validateMaxQuestionCount(Integer maxQuestionCount) {
        if (maxQuestionCount == null
                || maxQuestionCount < MIN_QUESTION_COUNT
                || maxQuestionCount > MAX_QUESTION_COUNT) {
            throw new ConversationException(ConversationErrorCode.INVALID_MAX_QUESTION_COUNT);
        }
    }

    private void validateContext(Long childId, CreateConversationRequest request) {
        if (request.contextType() == null || request.conversationGoal() == null) {
            throw new ConversationException(ConversationErrorCode.INVALID_CONVERSATION_CONTEXT);
        }

        if (request.contextType() == ContextType.GAME) {
            if (request.gameSessionId() == null) {
                throw new ConversationException(ConversationErrorCode.INVALID_CONVERSATION_CONTEXT);
            }
            if (!gameSessionService.existsForChild(request.gameSessionId(), childId)) {
                throw new ConversationException(ConversationErrorCode.GAME_SESSION_NOT_FOUND);
            }
            return;
        }

        if (request.gameSessionId() != null) {
            throw new ConversationException(ConversationErrorCode.INVALID_CONVERSATION_CONTEXT);
        }
    }

    private void validateNoActiveConversation(Long childId) {
        if (conversationSessionRepository.existsByChildIdAndStatusIn(childId, ACTIVE_STATUSES)) {
            throw new ConversationException(ConversationErrorCode.ACTIVE_CONVERSATION_EXISTS);
        }
    }

    private String generateConversationId() {
        return "conv_" + UUID.randomUUID().toString().replace("-", "");
    }
}
