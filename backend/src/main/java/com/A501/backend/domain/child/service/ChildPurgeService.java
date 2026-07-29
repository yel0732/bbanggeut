package com.A501.backend.domain.child.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
