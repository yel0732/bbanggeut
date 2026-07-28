package com.A501.backend.domain.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.auth.entity.Parent;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {

    private final ParentFinder parentFinder;

    @Transactional
    public void updateProfileImage(Long parentId, int profileImageNum) {
        Parent parent = parentFinder.getActive(parentId);
        parent.changeProfileImage(profileImageNum);
    }
}
