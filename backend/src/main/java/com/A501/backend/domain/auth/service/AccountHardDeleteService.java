package com.A501.backend.domain.auth.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.A501.backend.domain.auth.security.AccountPurgeProperties;
import com.A501.backend.domain.child.service.ChildPurgeService;
import com.A501.backend.domain.childdiary.service.ChildDiaryPurgeService;
import com.A501.backend.domain.conversation.service.ConversationPurgeService;
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
 * <p>도메인 삭제 순서는 AccountHardDeleteServiceTest가 InOrder로 고정한다. 도메인 간 참조에는
 * FK가 없어 순서를 틀려도 DB가 막지 않고 고아 데이터가 조용히 남기 때문이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountHardDeleteService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    // 이 횟수에 도달하면 수동 확인 요청을 한 번 남기고, 이후에는 스택트레이스를 생략한다.
    static final int FAILURE_LOG_THRESHOLD = 10;

    // parentId별 연속 실패 횟수. 스케줄러 스레드 하나가 순차 실행하지만, 배치 실행 중 다른
    // 경로에서 이 서비스를 호출할 수 있어 동시 접근에 안전한 맵을 쓴다.
    private final Map<Long, Integer> accountFailures = new ConcurrentHashMap<>();

    private final AccountPurgeProperties properties;
    private final AuthPurgeService authPurgeService;
    private final ProfilePurgeService profilePurgeService;
    private final ChildPurgeService childPurgeService;
    private final ReportPurgeService reportPurgeService;
    private final ChildDiaryPurgeService childDiaryPurgeService;
    private final ConversationPurgeService conversationPurgeService;
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
                accountFailures.remove(parentId);
            } catch (Exception e) {
                logRepeatedFailure(accountFailures, parentId, e, "계정");
            }
        }
    }

    /**
     * 같은 계정이 계속 실패할 때 로그가 무한히 쌓이는 것을 막는다.
     *
     * <p>운영 주기(5분)에서 영구 실패 계정 하나가 하루 288회 × 스택트레이스를 남기면, 디스크를
     * 채우는 것보다 <b>다른 에러가 그 노이즈에 묻히는 것</b>이 더 문제다. 영구 실패가 "원래 나는
     * 에러"처럼 보여 아무도 손대지 않게 된다.
     *
     * <p>임계치까지는 스택트레이스를 남겨 원인을 볼 수 있게 하고, 넘어서면 한 번만 수동 확인
     * 요청을 남긴 뒤 메시지만 기록한다. 신규 테이블을 만들지 않기로 했으므로 메모리 카운터이며,
     * 인스턴스 재시작 시 초기화된다(정밀 추적이 필요해지면 테이블 도입을 재검토한다).
     */
    private void logRepeatedFailure(
            Map<Long, Integer> counters, Long parentId, Exception e, String what) {
        int failures = counters.merge(parentId, 1, Integer::sum);

        if (failures < FAILURE_LOG_THRESHOLD) {
            log.error("{} hard delete 실패: parentId={}, 연속 {}회", what, parentId, failures, e);
        } else if (failures == FAILURE_LOG_THRESHOLD) {
            log.error("{} hard delete가 {}회 연속 실패했다 — 수동 확인이 필요하다: parentId={}. "
                    + "이후 주기부터는 스택트레이스를 남기지 않는다.", what, failures, parentId, e);
        } else {
            log.warn("{} hard delete 실패 지속: parentId={}, 연속 {}회 ({})",
                    what, parentId, failures, e.getClass().getSimpleName());
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
        conversationPurgeService.purgeByChildIds(childIds);
        gamePurgeService.purgeByChildIds(childIds);

        // refresh token은 parent보다 먼저여야 한다(FK + @ManyToOne).
        authPurgeService.hardDeleteRefreshTokens(parentId);
        childPurgeService.hardDeleteByParentId(parentId);
        // parent는 반드시 마지막. 먼저 지우면 재시도 대상을 찾을 수 없어 나머지가 고아로 남는다.
        authPurgeService.hardDeleteParent(parentId);
    }
}
