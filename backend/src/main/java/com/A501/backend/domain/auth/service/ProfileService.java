package com.A501.backend.domain.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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

    // 웹캠으로 찍은 사진을 올려 그 자리에서 프로필로 적용한다. 업로드와 적용을 한 요청으로 끝내는
    // 이유는, 올려두고 적용하지 않은 orphan 객체(아이 실물 사진)가 버킷에 남지 않게 하기 위함이다.
    @Transactional
    public String uploadProfileImage(Long parentId, MultipartFile file) {
        Parent parent = parentFinder.getActive(parentId);
        String previousKey = parent.getProfileImageKey();

        // 새 사진을 먼저 저장한다 — 검증이나 업로드가 실패하면 기존 사진이 그대로 남아야 한다.
        String uploadedKey = profileImageService.uploadImage(profileImageService.parentImagePrefix(parentId), file);
        profileImageService.deleteIfUpload(previousKey);
        parent.changeProfileImage(uploadedKey);

        return profileImageService.resolveUrl(uploadedKey);
    }
}
