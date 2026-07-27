package com.A501.backend.domain.childdiary.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.A501.backend.domain.childdiary.port.ChildDiaryCurrentParentProvider;

@Component
@Profile("local")
public class LocalChildDiaryCurrentParentProvider implements ChildDiaryCurrentParentProvider {
    private final Long parentId;
    public LocalChildDiaryCurrentParentProvider(@Value("${app.local.mock-principal-id:1}") Long parentId) {
        this.parentId = parentId;
    }
    @Override public Long getCurrentParentId() { return parentId; }
}
