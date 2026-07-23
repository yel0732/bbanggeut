package com.A501.backend.domain.conversation.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;

@Component
@Profile("local")
public class LocalChildAccessVerifier implements ChildAccessVerifier {

    private final Long mockChildId;
    private final Long mockChildOwnerId;

    public LocalChildAccessVerifier(
            @Value("${app.local.mock-child-id:15}") Long mockChildId,
            @Value("${app.local.mock-child-owner-id:1}") Long mockChildOwnerId) {
        this.mockChildId = mockChildId;
        this.mockChildOwnerId = mockChildOwnerId;
    }

    @Override
    public void verifyAccess(Long memberId, Long childId) {
        if (!mockChildId.equals(childId)) {
            throw new ConversationException(ConversationErrorCode.CHILD_NOT_FOUND);
        }
        if (!mockChildOwnerId.equals(memberId)) {
            throw new ConversationException(ConversationErrorCode.CHILD_ACCESS_DENIED);
        }
    }
}
