package com.A501.backend.domain.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.common.storage.ProfileImageService;
import com.A501.backend.domain.auth.repository.ParentRepository;

import lombok.RequiredArgsConstructor;

/**
 * 계정 파기 시 부모 프로필 이미지의 **잔여 객체**를 정리한다.
 *
 * <p>탈퇴 API(DELETE /api/auth/me)가 이미 업로드 객체를 즉시 파기하고 키를 기본 프리셋으로
 * 되돌리므로 대부분 no-op이다. 탈퇴 중 S3 삭제가 실패했거나 어떤 경로로 업로드 키가 남은
 * 경우를 대비한 안전망이다.
 *
 * <p>키를 되돌리지 않고 객체만 지운다 — 직후에 parent row 자체가 삭제되므로 의미가 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfilePurgeService {

    private final ParentRepository parentRepository;
    private final ProfileImageService profileImageService;

    public void deleteParentProfileImage(Long parentId) {
        if (parentId == null) {
            return;
        }
        parentRepository.findById(parentId)
                .map(parent -> parent.getProfileImageKey())
                .ifPresent(profileImageService::deleteIfUpload);
    }
}
