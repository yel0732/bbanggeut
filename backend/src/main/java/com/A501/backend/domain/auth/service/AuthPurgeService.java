package com.A501.backend.domain.auth.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.auth.entity.Parent;
import com.A501.backend.domain.auth.repository.ParentRepository;
import com.A501.backend.domain.auth.repository.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 탈퇴 계정의 물리 파기 중 auth 도메인이 담당하는 부분.
 *
 * <p>각 메서드는 자기 트랜잭션을 갖는다. 파기 전체를 한 트랜잭션으로 묶으면 대량 삭제 시 락
 * 점유 시간이 길어지고, 어차피 모든 단계가 idempotent라 중간 실패는 다음 주기 재시도로 수렴한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthPurgeService {

    private final ParentRepository parentRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * 파기 대상 계정 ID를 오래 전에 탈퇴한 순서로 최대 {@code limit}건 반환한다.
     *
     * <p>limit이 0 이하면 조회하지 않고 빈 목록을 준다. PageRequest가 size 0에서 예외를 던지므로
     * 설정값이 잘못 들어왔을 때 스케줄러 전체가 죽는 것을 막는다.
     */
    public List<Long> findWithdrawnParentIdsForPurge(LocalDateTime purgeBefore, int limit) {
        if (purgeBefore == null || limit < 1) {
            return List.of();
        }
        return parentRepository.findIdsToPurge(purgeBefore, PageRequest.of(0, limit));
    }

    /**
     * 탈퇴(소프트 삭제)된 계정인지 확인한다.
     *
     * <p>오케스트레이션 진입점의 안전장치다. 하위 purge service들은 부모 상태를 보지 않고
     * 넘겨받은 parentId의 데이터를 전부 지우는데(타 도메인이 parent를 조회하면 도메인 침범),
     * child_id·parent_id에 FK가 없어 활성 계정 ID가 잘못 들어와도 DB가 막지 않는다.
     */
    public boolean isWithdrawn(Long parentId) {
        if (parentId == null) {
            return false;
        }
        return parentRepository.findById(parentId)
                .map(Parent::isDeleted)
                .orElse(false);
    }

    /**
     * 해당 부모의 refresh token을 전부 물리 삭제한다.
     * parent 삭제보다 반드시 먼저 호출해야 한다(FK 제약).
     */
    @Transactional
    public void hardDeleteRefreshTokens(Long parentId) {
        if (parentId == null) {
            return;
        }
        int deleted = refreshTokenRepository.deleteByParentId(parentId);
        if (deleted > 0) {
            log.info("refresh token hard delete 완료: parentId={}, count={}", parentId, deleted);
        }
    }

    /**
     * parent row를 물리 삭제한다. **오케스트레이션의 마지막 단계여야 한다.**
     * 먼저 지우면 다음 주기에 재시도 대상을 찾을 수 없어 나머지 데이터가 고아로 남는다.
     *
     * <p>이미 없으면 조용히 넘어간다 — 이전 주기에서 여기까지 성공했는데 로그만 실패한 경우처럼,
     * 같은 계정을 두 번 처리하는 상황이 정상적으로 발생한다.
     */
    @Transactional
    public void hardDeleteParent(Long parentId) {
        if (parentId == null || !parentRepository.existsById(parentId)) {
            return;
        }
        parentRepository.deleteById(parentId);
        log.info("계정 hard delete 완료: parentId={}", parentId);
    }
}
