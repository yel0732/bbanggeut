package com.A501.backend.domain.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.auth.entity.Parent;
import com.A501.backend.domain.auth.exception.AuthErrorCode;
import com.A501.backend.domain.auth.repository.ParentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {

    private final ParentRepository parentRepository;

    @Transactional
    public void updateProfileImage(Long parentId, int profileImageNum) {
        // 소프트 삭제된 Parent는 걸러낸다 — PinService.getParent와 동일한 이유.
        Parent parent = parentRepository.findById(parentId)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.PARENT_NOT_FOUND));
        parent.changeProfileImage(profileImageNum);
    }
}
