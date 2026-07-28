package com.A501.backend.domain.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.common.storage.ProfileImageService;
import com.A501.backend.domain.auth.entity.Parent;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {

    private final ParentFinder parentFinder;
    private final ProfileImageService profileImageService;

    @Transactional
    public String updateProfileImage(Long parentId, String imageKey) {
        // 키 검증을 먼저 한다 — 거절될 요청 때문에 이전 이미지를 지우는 일이 없도록.
        profileImageService.assertValidPreset(imageKey);

        Parent parent = parentFinder.getActive(parentId);
        // 교체 전 이미지가 업로드(웹캠) 사진이면 즉시 파기한다. 더 이상 참조되지 않는 아이 실물
        // 사진을 남겨둘 이유가 없다(프리셋은 공유 자원이라 지우지 않는다).
        profileImageService.deleteIfUpload(parent.getProfileImageKey());
        parent.changeProfileImage(imageKey);

        return profileImageService.resolveUrl(imageKey);
    }
}
