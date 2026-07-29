package com.A501.backend.domain.game.service;

public interface GameChildAccessVerifier {

    void verifyAccess(Long memberId, Long childId);
}
