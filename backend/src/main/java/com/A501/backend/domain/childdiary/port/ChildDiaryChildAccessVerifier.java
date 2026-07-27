package com.A501.backend.domain.childdiary.port;

public interface ChildDiaryChildAccessVerifier {
    void verifyOwnership(Long parentId, Long childId);
}
