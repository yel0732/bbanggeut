package com.A501.backend.domain.child.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.common.storage.ProfileImageService;
import com.A501.backend.domain.auth.exception.PinErrorCode;
import com.A501.backend.domain.auth.service.ParentFinder;
import com.A501.backend.domain.auth.service.PinService;
import com.A501.backend.domain.child.dto.response.ChildResponse;
import com.A501.backend.domain.child.entity.Child;
import com.A501.backend.domain.child.exception.ChildErrorCode;
import com.A501.backend.domain.child.repository.ChildRepository;
import com.A501.backend.domain.child.security.ChildProperties;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChildService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ChildRepository childRepository;
    private final ChildProperties childProperties;
    private final ParentFinder parentFinder;
    private final PinService pinService;
    private final ProfileImageService profileImageService;

    // 탈퇴 계정의 만료 전 access token을 걸러낸다. JwtAuthenticationFilter는 서명만 보고
    // 탈퇴 여부는 확인하지 않으므로 서비스 계층이 유일한 차단 지점이다.
    //
    // 게이트가 없으면 응답이 경로마다 어긋난다 — 조회는 200 빈 배열, 수정·삭제는 자녀가 소프트
    // 삭제된 탓에 404가 나갔다. 프론트는 401을 기준으로 로그아웃 처리하므로 전부 401로 맞춘다.
    public boolean isNameAvailable(Long parentId, String name) {
        parentFinder.assertActive(parentId);
        return !childRepository.existsByParentIdAndNameAndDeletedAtIsNull(parentId, name);
    }

    public List<ChildResponse> listChildren(Long parentId) {
        parentFinder.assertActive(parentId);
        return childRepository.findAllByParentIdAndDeletedAtIsNull(parentId).stream()
                .map(this::toResponse)
                .toList();
    }

    // 부모 탈퇴 시 그 부모의 활성 자녀를 모두 소프트 삭제한다. auth 도메인(AuthService)이
    // 서비스 계층을 통해 호출하며, child 엔티티를 직접 참조하지 않도록 이 메서드로만 노출한다.
    @Transactional
    public void softDeleteChildrenOf(Long parentId) {
        LocalDateTime now = LocalDateTime.now(KST);
        childRepository.findAllByParentIdAndDeletedAtIsNull(parentId)
                .forEach(child -> {
                    child.softDelete(now);
                    purgeUploadedImage(child);
                });
    }

    @Transactional
    public ChildResponse createChild(Long parentId, String name, String imageKey) {
        if (!pinService.hasPin(parentId)) {
            throw new BusinessException(PinErrorCode.PIN_NOT_SET);
        }
        if (childRepository.countByParentIdAndDeletedAtIsNull(parentId) >= childProperties.maxProfileCount()) {
            throw new BusinessException(ChildErrorCode.CHILD_LIMIT_EXCEEDED);
        }
        if (childRepository.existsByParentIdAndNameAndDeletedAtIsNull(parentId, name)) {
            throw new BusinessException(ChildErrorCode.CHILD_NAME_ALREADY_EXISTS);
        }

        Child child = childRepository.save(Child.create(parentId, name, resolveCreateKey(imageKey)));
        return toResponse(child);
    }

    @Transactional
    public ChildResponse updateChild(Long parentId, Long childId, String name, String imageKey) {
        parentFinder.assertActive(parentId);
        // 키 검증을 먼저 한다 — 거절될 요청 때문에 이전 이미지를 지우는 일이 없도록.
        profileImageService.assertValidPreset(imageKey);

        Child child = findOwnedChild(parentId, childId);
        if (!child.getName().equals(name) && childRepository.existsByParentIdAndNameAndDeletedAtIsNull(parentId, name)) {
            throw new BusinessException(ChildErrorCode.CHILD_NAME_ALREADY_EXISTS);
        }
        child.rename(name);
        // 교체 전 이미지가 업로드(웹캠) 사진이면 즉시 파기한다 — 아이 실물 사진을 남겨두지 않는다.
        profileImageService.deleteIfUpload(child.getProfileImageKey());
        child.changeProfileImage(imageKey);

        return toResponse(child);
    }

    // 웹캠으로 찍은 사진을 올려 그 자리에서 자녀 프로필로 적용한다. 자녀 생성 시점에는 childId가
    // 없어 키를 만들 수 없으므로, 웹캠 사진은 생성 이후 이 경로로만 붙는다.
    @Transactional
    public ChildResponse uploadChildImage(Long parentId, Long childId, MultipartFile file) {
        parentFinder.assertActive(parentId);
        // 소유권 검증을 먼저 한다 — 남의 자녀 경로에 사진을 올리려는 시도를 업로드 전에 막는다.
        Child child = findOwnedChild(parentId, childId);
        String previousKey = child.getProfileImageKey();

        // 새 사진을 먼저 저장한다 — 검증이나 업로드가 실패하면 기존 사진이 그대로 남아야 한다.
        String uploadedKey = profileImageService.uploadImage(
                profileImageService.childImagePrefix(parentId, childId), file);
        profileImageService.deleteIfUpload(previousKey);
        child.changeProfileImage(uploadedKey);

        return toResponse(child);
    }

    // noRollbackFor 필요: 이 메서드가 먼저 트랜잭션을 열기 때문에 pinService.verifyPin(...)
    // 호출은 새 트랜잭션이 아니라 이 트랜잭션에 합류한다. 이 메서드에 noRollbackFor가 없으면
    // verifyPin이 늘려놓은 PIN 실패 카운트까지 이 메서드의 기본 롤백 규칙에 밀려 통째로
    // 롤백된다 — PinService 안에서 겪은 self-invocation 문제와 같은 종류의 버그다.
    @Transactional(noRollbackFor = BusinessException.class)
    public void deleteChild(Long parentId, Long childId, String pin) {
        parentFinder.assertActive(parentId);
        // 소유권 검증(404)을 PIN 검증보다 먼저 한다 — 어차피 404로 끝날 요청 때문에
        // PIN 실패 카운터를 소모하지 않기 위함이다. PIN 검증은 대상 자녀와 무관하게 호출자
        // 본인 계정만 보므로, 순서를 바꿔도 자녀 존재 여부가 새어나가지는 않는다.
        Child child = findOwnedChild(parentId, childId);
        pinService.verifyPin(parentId, pin);
        child.softDelete(LocalDateTime.now(KST));
        purgeUploadedImage(child);
    }

    // 소프트 삭제라 DB 행은 남지만 아이 실물 사진은 즉시 파기한다. 대화·게임·리포트 등 타 도메인의
    // hard-delete purge는 팀 논의 대기 중이지만, 이 사진은 child 도메인 소유 리소스라 지금 처리한다.
    private void purgeUploadedImage(Child child) {
        child.changeProfileImage(profileImageService.purgeUploadedImage(
                child.getProfileImageKey(), profileImageService.defaultChildPresetKey()));
    }

    // 생성 시점에는 childId가 없어 웹캠 사진의 키를 만들 수 없으므로 프리셋만 허용한다.
    // 생략하면 기본 프리셋으로 시작하고, 웹캠 사진은 생성 후 별도 업로드로 붙인다.
    private String resolveCreateKey(String imageKey) {
        if (imageKey == null || imageKey.isBlank()) {
            return profileImageService.defaultChildPresetKey();
        }
        profileImageService.assertValidPreset(imageKey);
        return imageKey;
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

    private ChildResponse toResponse(Child child) {
        return ChildResponse.from(child, profileImageService.resolveUrl(child.getProfileImageKey()));
    }
}
