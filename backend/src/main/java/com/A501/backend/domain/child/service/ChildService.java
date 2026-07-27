package com.A501.backend.domain.child.service;

import java.util.List;

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

    public List<Child> listChildren(Long parentId) {
        return childRepository.findAllByParentIdAndDeletedAtIsNull(parentId);
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

    @Transactional
    public Child updateChild(Long parentId, Long childId, String name, int profileImageNum) {
        Child child = findOwnedChild(parentId, childId);
        if (!child.getName().equals(name) && childRepository.existsByParentIdAndNameAndDeletedAtIsNull(parentId, name)) {
            throw new BusinessException(ChildErrorCode.CHILD_NAME_ALREADY_EXISTS);
        }
        child.rename(name);
        child.changeProfileImage(profileImageNum);
        return child;
    }

    // childId 경로 변수를 받는 모든 엔드포인트가 공유하는 소유권 검증. 존재하지 않거나 다른
    // 부모 소유면 둘 다 404로 통일한다 — 403을 쓰면 "리소스는 있는데 네 것이 아니다"가 드러나
    // 리소스 존재 자체가 노출된다.
    private Child findOwnedChild(Long parentId, Long childId) {
        Child child = childRepository.findByChildIdAndDeletedAtIsNull(childId)
                .orElseThrow(() -> new BusinessException(ChildErrorCode.CHILD_NOT_FOUND));
        if (!child.isOwnedBy(parentId)) {
            throw new BusinessException(ChildErrorCode.CHILD_NOT_FOUND);
        }
        return child;
    }
}
