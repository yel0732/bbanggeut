package com.A501.backend.domain.conversation.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.A501.backend.domain.conversation.dto.realtime.RealtimeChildContext;
import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;

/**
 * Temporary adapter used only by the opt-in container-test profile while the
 * production conversation ownership adapter is under development.
 */
@Component
@Profile("container-test")
public class ContainerTestConversationAdapter
        implements ChildAccessVerifier, CurrentMemberProvider, RealtimeChildProfileProvider {

    private final Long memberId;
    private final Long childId;

    public ContainerTestConversationAdapter(
            @Value("${app.container-test.member-id:1}") Long memberId,
            @Value("${app.container-test.child-id:1}") Long childId) {
        this.memberId = memberId;
        this.childId = childId;
    }

    @Override
    public void verifyAccess(Long requestedMemberId, Long requestedChildId) {
        if (!childId.equals(requestedChildId)) {
            throw new ConversationException(ConversationErrorCode.CHILD_NOT_FOUND);
        }
        if (!memberId.equals(requestedMemberId)) {
            throw new ConversationException(ConversationErrorCode.CHILD_ACCESS_DENIED);
        }
    }

    @Override
    public Long getCurrentMemberId() {
        return memberId;
    }

    @Override
    public RealtimeChildContext getProfile(Long requestedChildId) {
        verifyAccess(memberId, requestedChildId);
        return new RealtimeChildContext("container-test-child");
    }
}
