package com.A501.backend.domain.childdiary.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.childdiary.exception.ChildDiaryErrorCode;
import com.A501.backend.domain.childdiary.port.ChildDiaryChildAccessVerifier;

@Component
@Profile("local")
public class LocalChildDiaryChildAccessVerifier implements ChildDiaryChildAccessVerifier {
    private final Long childId;
    private final Long parentId;
    public LocalChildDiaryChildAccessVerifier(
            @Value("${app.local.mock-child-id:15}") Long childId,
            @Value("${app.local.mock-child-owner-id:1}") Long parentId) {
        this.childId = childId;
        this.parentId = parentId;
    }
    @Override public void verifyOwnership(Long parentId, Long childId) {
        if (!this.parentId.equals(parentId) || !this.childId.equals(childId)) {
            throw new BusinessException(ChildDiaryErrorCode.CHILD_NOT_FOUND);
        }
    }
}
