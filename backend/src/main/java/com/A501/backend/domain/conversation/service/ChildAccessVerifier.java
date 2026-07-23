package com.A501.backend.domain.conversation.service;

public interface ChildAccessVerifier {

    void verifyAccess(Long memberId, Long childId);
}
