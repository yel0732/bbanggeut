package com.A501.backend.domain.child.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.auth.exception.PinErrorCode;
import com.A501.backend.domain.auth.service.PinService;
import com.A501.backend.domain.child.entity.Child;
import com.A501.backend.domain.child.exception.ChildErrorCode;
import com.A501.backend.domain.child.repository.ChildRepository;
import com.A501.backend.domain.child.security.ChildProperties;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChildService {

    private final ChildRepository childRepository;
    private final ChildProperties childProperties;
    private final PinService pinService;

    public boolean isNameAvailable(Long parentId, String name) {
        return !childRepository.existsByParentIdAndNameAndDeletedAtIsNull(parentId, name);
    }

    @Transactional
    public Child createChild(Long parentId, String name, int profileImageNum) {
        if (!pinService.hasPin(parentId)) {
            throw new BusinessException(PinErrorCode.PIN_NOT_SET);
        }
        if (childRepository.countByParentIdAndDeletedAtIsNull(parentId) >= childProperties.maxProfileCount()) {
            throw new BusinessException(ChildErrorCode.CHILD_LIMIT_EXCEEDED);
        }
        if (childRepository.existsByParentIdAndNameAndDeletedAtIsNull(parentId, name)) {
            throw new BusinessException(ChildErrorCode.CHILD_NAME_ALREADY_EXISTS);
        }
        return childRepository.save(Child.create(parentId, name, profileImageNum));
    }
}
