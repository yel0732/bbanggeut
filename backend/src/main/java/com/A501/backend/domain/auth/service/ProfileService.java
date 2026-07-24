package com.A501.backend.domain.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.auth.entity.Parent;
import com.A501.backend.domain.auth.repository.ParentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {

    private final ParentRepository parentRepository;

    @Transactional
    public void updateProfileImage(Long parentId, int profileImageNum) {
        Parent parent = parentRepository.findById(parentId)
                .orElseThrow(() -> new IllegalStateException(
                        "인증된 parentId에 해당하는 Parent를 찾을 수 없습니다: " + parentId));
        parent.changeProfileImage(profileImageNum);
    }
}
