package com.A501.backend.domain.child.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.common.storage.ProfileImageService;
import com.A501.backend.domain.child.repository.ChildRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 계정 파기 시 child 도메인이 담당하는 부분.
 *
 * <p>총괄(auth)이 child 리포지토리를 직접 참조하지 않도록 이 서비스를 통해서만 접근한다.
 * 다른 도메인의 {@code *PurgeService}와 같은 역할이다.
 *
 * <p><b>부모의 탈퇴 여부는 여기서 확인하지 않는다.</b> child 도메인이 parent 테이블을 조회하면
 * 도메인 침범이기 때문이다. 호출자(AccountHardDeleteService)가 탈퇴한 계정만 넘긴다는 전제로
 * 동작하며, 그 확인은 호출자 쪽에 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChildPurgeService {

    private final ChildRepository childRepository;
    private final ProfileImageService profileImageService;

    /**
     * 파기 대상 자녀 ID. **소프트 삭제된 자녀를 포함한다** — 탈퇴 시 자녀도 함께 소프트 삭제되므로
     * 포함하지 않으면 대상이 비어 다른 도메인 데이터가 전부 남는다.
     */
    public List<Long> findChildIdsIncludingDeleted(Long parentId) {
        if (parentId == null) {
            return List.of();
        }
        return childRepository.findAllChildIdsByParentId(parentId);
    }

    /**
     * 자녀 프로필 이미지의 **잔여 객체**를 정리한다. 탈퇴 API가 이미 파기했으므로 대부분 no-op인
     * 안전망이다. 직후에 child row가 삭제되므로 키를 되돌리지는 않는다.
     *
     * <p>한 자녀의 S3 삭제 실패가 나머지를 막지 않게 개별로 처리한다 — 여기서 예외가 나면
     * 아래 hardDeleteByParentId까지 도달하지 못해 계정 파기 전체가 매 주기 실패한다.
     */
    public void deleteChildProfileImagesByParentId(Long parentId) {
        if (parentId == null) {
            return;
        }
        for (String key : childRepository.findAllProfileImageKeysByParentId(parentId)) {
            try {
                profileImageService.deleteIfUpload(key);
            } catch (RuntimeException e) {
                // 키 원문은 남기지 않는다(개인정보). 남은 객체는 스토리지 수명주기 정책에 맡긴다.
                log.warn("자녀 프로필 이미지 정리 실패: parentId={}", parentId, e);
            }
        }
    }

    // --- 개별 삭제된 자녀 파기 ---

    /**
     * 소프트 삭제된 자녀를 가진 보호자 ID. 부모의 탈퇴 여부는 걸러내지 않으므로
     * 호출자가 auth 도메인에 물어 활성 계정만 남겨야 한다.
     */
    public List<Long> findParentIdsWithDeletedChildren(LocalDateTime purgeBefore, int limit) {
        if (purgeBefore == null || limit < 1) {
            return List.of();
        }
        return childRepository.findParentIdsWithDeletedChildren(purgeBefore, PageRequest.of(0, limit));
    }

    public List<Long> findDeletedChildIds(Long parentId, LocalDateTime purgeBefore) {
        if (parentId == null || purgeBefore == null) {
            return List.of();
        }
        return childRepository.findDeletedChildIds(parentId, purgeBefore);
    }

    /** 지정한 자녀들의 프로필 이미지 잔여 객체를 정리한다. 개별 자녀 파기용. */
    public void deleteProfileImagesByChildIds(List<Long> childIds) {
        if (childIds == null || childIds.isEmpty()) {
            return;
        }
        for (String key : childRepository.findProfileImageKeysByChildIds(childIds)) {
            try {
                profileImageService.deleteIfUpload(key);
            } catch (RuntimeException e) {
                log.warn("자녀 프로필 이미지 정리 실패: childIds={}건", childIds.size(), e);
            }
        }
    }

    /** 지정한 자녀들을 물리 삭제한다. 자녀를 참조하는 도메인 삭제 이후에 호출해야 한다. */
    @Transactional
    public void hardDeleteByChildIds(List<Long> childIds) {
        if (childIds == null || childIds.isEmpty()) {
            return;
        }
        int deleted = childRepository.deleteByChildIds(childIds);
        if (deleted > 0) {
            log.info("삭제된 자녀 hard delete 완료: count={}", deleted);
        }
    }

    /**
     * 해당 부모의 자녀를 전부 물리 삭제한다.
     * 자녀를 참조하는 도메인(report·conversation·game·diary) 삭제가 끝난 뒤 호출해야 한다.
     * child_id에는 FK가 없어 순서를 틀려도 DB가 막지 않는다.
     */
    @Transactional
    public void hardDeleteByParentId(Long parentId) {
        if (parentId == null) {
            return;
        }
        int deleted = childRepository.deleteByParentId(parentId);
        if (deleted > 0) {
            log.info("자녀 hard delete 완료: parentId={}, count={}", parentId, deleted);
        }
    }
}
