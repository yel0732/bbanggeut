package com.A501.backend.domain.auth.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;

import com.A501.backend.domain.auth.security.AccountPurgeProperties;
import com.A501.backend.domain.child.service.ChildPurgeService;
import com.A501.backend.domain.childdiary.service.ChildDiaryPurgeService;
import com.A501.backend.domain.game.service.GamePurgeService;
import com.A501.backend.domain.report.service.ReportPurgeService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 탈퇴 계정의 데이터를 도메인 전반에 걸쳐 물리 삭제한다.
 *
 * <p>여기서 트랜잭션을 열지 않는다. 각 purge service가 자기 트랜잭션을 가지며, 전체를 하나로
 * 묶으면 대량 삭제 시 락 점유 시간이 길어진다. 모든 단계가 idempotent라 중간에 실패해 일부만
 * 삭제된 상태로 남아도 다음 주기 재시도로 수렴한다.
 *
 * <p>conversation 도메인은 아직 childIds 기반 purge를 제공하지 않아 연결되지 않았다.
 * 그래서 <b>이 서비스만으로는 파기가 완결되지 않는다</b> — 대화 데이터가 남는다.
 * 자세한 내용은 docs/account-hard-delete-role-plan.md 참고.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountHardDeleteService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AccountPurgeProperties properties;
    private final AuthPurgeService authPurgeService;
    private final ProfilePurgeService profilePurgeService;
    private final ChildPurgeService childPurgeService;
    private final ReportPurgeService reportPurgeService;
    private final ChildDiaryPurgeService childDiaryPurgeService;
    private final GamePurgeService gamePurgeService;

    /**
     * 한 주기에 처리할 계정을 조회해 순서대로 파기한다.
     * 한 계정의 실패가 나머지를 막지 않으며, 실패한 계정은 남아 다음 주기에 재시도된다.
     */
    public void purgeBatch() {
        LocalDateTime purgeBefore = properties.purgeBefore(LocalDateTime.now(KST));
        List<Long> parentIds =
                authPurgeService.findWithdrawnParentIdsForPurge(purgeBefore, properties.batchSize());
        if (parentIds.isEmpty()) {
            return;
        }

        log.info("계정 hard delete 시작: 대상={}건", parentIds.size());
        for (Long parentId : parentIds) {
            try {
                purgeWithdrawnParent(parentId);
            } catch (Exception e) {
                log.error("계정 hard delete 실패: parentId={}", parentId, e);
            }
        }
    }

    /**
     * 계정 하나를 파기한다. 삭제 순서는 leaf 우선이며, 그 근거는 아래 주석과
     * AccountHardDeleteServiceTest의 순서 검증이 함께 고정한다.
     */
    public void purgeWithdrawnParent(Long parentId) {
        // 조회와 실제 삭제 사이에 시간차가 있고 이 메서드가 직접 호출될 수도 있다. 하위 purge
        // service는 부모 상태를 확인하지 않으므로, 활성 계정이 들어오면 여기서 막아야 한다.
        if (!authPurgeService.isWithdrawn(parentId)) {
            log.warn("탈퇴 상태가 아닌 계정의 파기 요청을 무시한다: parentId={}", parentId);
            return;
        }

        // childIds는 이후 모든 도메인 삭제의 입력이므로 child row를 지우기 전에 확보한다.
        List<Long> childIds = childPurgeService.findChildIdsIncludingDeleted(parentId);

        // 탈퇴 API에서 이미 파기됐으면 no-op인 안전망.
        profilePurgeService.deleteParentProfileImage(parentId);
        childPurgeService.deleteChildProfileImagesByParentId(parentId);

        // child_id를 참조하는 도메인들. child row보다 먼저 지워야 고아가 남지 않는다.
        reportPurgeService.purgeByParentIdAndChildIds(parentId, childIds);
        childDiaryPurgeService.purgeByChildIds(childIds);
        gamePurgeService.purgeByChildIds(childIds);
        // TODO: conversationPurgeService.purgeByChildIds(childIds) — 담당자 구현 대기

        // refresh token은 parent보다 먼저여야 한다(FK + @ManyToOne).
        authPurgeService.hardDeleteRefreshTokens(parentId);
        childPurgeService.hardDeleteByParentId(parentId);
        // parent는 반드시 마지막. 먼저 지우면 재시도 대상을 찾을 수 없어 나머지가 고아로 남는다.
        authPurgeService.hardDeleteParent(parentId);
    }
}
