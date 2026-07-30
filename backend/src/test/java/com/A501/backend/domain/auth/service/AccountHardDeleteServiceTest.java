package com.A501.backend.domain.auth.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.A501.backend.domain.auth.security.AccountPurgeProperties;
import com.A501.backend.domain.child.service.ChildPurgeService;
import com.A501.backend.domain.childdiary.service.ChildDiaryPurgeService;
import com.A501.backend.domain.conversation.service.ConversationPurgeService;
import com.A501.backend.domain.game.service.GamePurgeService;
import com.A501.backend.domain.report.service.ReportPurgeService;

@ExtendWith(MockitoExtension.class)
class AccountHardDeleteServiceTest {

    private static final Long PARENT_ID = 42L;
    private static final List<Long> CHILD_IDS = List.of(10L, 11L);
    private static final java.time.LocalDateTime PURGE_BEFORE =
            java.time.LocalDateTime.of(2026, 7, 30, 12, 0);

    @Mock
    private AuthPurgeService authPurgeService;
    @Mock
    private ProfilePurgeService profilePurgeService;
    @Mock
    private ChildPurgeService childPurgeService;
    @Mock
    private ReportPurgeService reportPurgeService;
    @Mock
    private ChildDiaryPurgeService childDiaryPurgeService;
    @Mock
    private ConversationPurgeService conversationPurgeService;
    @Mock
    private GamePurgeService gamePurgeService;

    private AccountHardDeleteService service(Duration gracePeriod, int batchSize) {
        return new AccountHardDeleteService(
                new AccountPurgeProperties(gracePeriod, batchSize),
                authPurgeService,
                profilePurgeService,
                childPurgeService,
                reportPurgeService,
                childDiaryPurgeService,
                conversationPurgeService,
                gamePurgeService);
    }

    private AccountHardDeleteService service() {
        return service(Duration.ZERO, 50);
    }

    // child_id·parent_id에는 FK가 없어 순서를 틀려도 DB가 막지 않는다. 이 검증이 유일한
    // 방어선이다: 자녀 참조 도메인 → child → parent 순서가 깨지면 고아 데이터가 조용히 남는다.
    @Test
    void purgeWithdrawnParent_deletesLeafDomainsBeforeChildAndParent() {
        when(authPurgeService.isWithdrawn(PARENT_ID)).thenReturn(true);
        when(childPurgeService.findChildIdsIncludingDeleted(PARENT_ID)).thenReturn(CHILD_IDS);

        service().purgeWithdrawnParent(PARENT_ID);

        InOrder order = inOrder(
                authPurgeService, profilePurgeService, childPurgeService,
                reportPurgeService, childDiaryPurgeService, conversationPurgeService,
                gamePurgeService);
        // childIds는 child row를 지우기 전에 확보해야 한다.
        order.verify(childPurgeService).findChildIdsIncludingDeleted(PARENT_ID);
        order.verify(profilePurgeService).deleteParentProfileImage(PARENT_ID);
        order.verify(childPurgeService).deleteChildProfileImagesByParentId(PARENT_ID);
        order.verify(reportPurgeService).purgeByParentIdAndChildIds(PARENT_ID, CHILD_IDS);
        order.verify(childDiaryPurgeService).purgeByChildIds(CHILD_IDS);
        order.verify(conversationPurgeService).purgeByChildIds(CHILD_IDS);
        order.verify(gamePurgeService).purgeByChildIds(CHILD_IDS);
        // refresh token은 parent보다 먼저(FK), parent는 반드시 마지막.
        order.verify(authPurgeService).hardDeleteRefreshTokens(PARENT_ID);
        order.verify(childPurgeService).hardDeleteByParentId(PARENT_ID);
        order.verify(authPurgeService).hardDeleteParent(PARENT_ID);
    }

    // 활성 계정 ID가 잘못 들어오면 그 집 데이터가 통째로 날아간다. 하위 purge service는
    // 부모 상태를 보지 않으므로 여기서 막지 못하면 되돌릴 수 없다.
    @Test
    void purgeWithdrawnParent_doesNothingWhenAccountIsNotWithdrawn() {
        when(authPurgeService.isWithdrawn(PARENT_ID)).thenReturn(false);

        service().purgeWithdrawnParent(PARENT_ID);

        verifyNoInteractions(
                profilePurgeService, childPurgeService, reportPurgeService,
                childDiaryPurgeService, conversationPurgeService, gamePurgeService);
        verify(authPurgeService, never()).hardDeleteParent(anyLong());
        verify(authPurgeService, never()).hardDeleteRefreshTokens(anyLong());
    }

    @Test
    void purgeBatch_continuesAfterOneAccountFails() {
        when(authPurgeService.findWithdrawnParentIdsForPurge(any(), anyInt()))
                .thenReturn(List.of(1L, 2L));
        when(authPurgeService.isWithdrawn(anyLong())).thenReturn(true);
        when(childPurgeService.findChildIdsIncludingDeleted(anyLong())).thenReturn(List.of());
        doThrow(new RuntimeException("boom")).when(authPurgeService).hardDeleteParent(1L);

        service().purgeBatch();

        // 1번이 터져도 2번은 처리되어야 한다.
        verify(authPurgeService).hardDeleteParent(2L);
    }

    @Test
    void purgeBatch_doesNothingWhenNoTargets() {
        when(authPurgeService.findWithdrawnParentIdsForPurge(any(), anyInt()))
                .thenReturn(List.of());

        service().purgeBatch();

        verifyNoInteractions(
                profilePurgeService, childPurgeService, reportPurgeService,
                childDiaryPurgeService, conversationPurgeService, gamePurgeService);
    }

    // --- 개별 삭제된 자녀 파기 ---

    // 탈퇴한 부모의 자녀는 계정 파기가 처리해야 한다. 여기서 먼저 child row를 지우면
    // 계정 파기가 childIds를 빈 목록으로 조회해 대화·리포트가 고아로 남는다.
    @Test
    void purgeDeletedChildrenBatch_skipsChildrenOfWithdrawnParents() {
        when(childPurgeService.findParentIdsWithDeletedChildren(any(), anyInt()))
                .thenReturn(List.of(PARENT_ID));
        when(authPurgeService.isWithdrawn(PARENT_ID)).thenReturn(true);

        service().purgeDeletedChildrenBatch();

        verify(childPurgeService, never()).findDeletedChildIds(anyLong(), any());
        verify(childPurgeService, never()).hardDeleteByChildIds(any());
        verifyNoInteractions(childDiaryPurgeService, conversationPurgeService, gamePurgeService);
    }

    @Test
    void purgeDeletedChildrenOf_deletesLeafDomainsBeforeTheChildRows() {
        when(childPurgeService.findDeletedChildIds(PARENT_ID, PURGE_BEFORE)).thenReturn(CHILD_IDS);

        service().purgeDeletedChildrenOf(PARENT_ID, PURGE_BEFORE);

        InOrder order = inOrder(
                childPurgeService, childDiaryPurgeService, conversationPurgeService, gamePurgeService);
        order.verify(childPurgeService).deleteProfileImagesByChildIds(CHILD_IDS);
        order.verify(childDiaryPurgeService).purgeByChildIds(CHILD_IDS);
        order.verify(conversationPurgeService).purgeByChildIds(CHILD_IDS);
        order.verify(gamePurgeService).purgeByChildIds(CHILD_IDS);
        order.verify(childPurgeService).hardDeleteByChildIds(CHILD_IDS);
    }

    // report는 아직 연결하지 않았다(자녀 범위 메서드 대기). 계정 파기용
    // purgeByParentIdAndChildIds를 실수로 부르면 형제 자녀의 알림까지 삭제된다.
    @Test
    void purgeDeletedChildrenOf_doesNotCallAccountScopedReportPurge() {
        when(childPurgeService.findDeletedChildIds(PARENT_ID, PURGE_BEFORE)).thenReturn(CHILD_IDS);

        service().purgeDeletedChildrenOf(PARENT_ID, PURGE_BEFORE);

        verifyNoInteractions(reportPurgeService);
    }

    @Test
    void purgeDeletedChildrenOf_doesNothingWhenNoDeletedChildren() {
        when(childPurgeService.findDeletedChildIds(PARENT_ID, PURGE_BEFORE)).thenReturn(List.of());

        service().purgeDeletedChildrenOf(PARENT_ID, PURGE_BEFORE);

        verify(childPurgeService, never()).hardDeleteByChildIds(any());
        verifyNoInteractions(childDiaryPurgeService, conversationPurgeService, gamePurgeService);
    }

    @Test
    void purgeDeletedChildrenBatch_continuesAfterOneParentFails() {
        when(childPurgeService.findParentIdsWithDeletedChildren(any(), anyInt()))
                .thenReturn(List.of(1L, 2L));
        when(authPurgeService.isWithdrawn(anyLong())).thenReturn(false);
        when(childPurgeService.findDeletedChildIds(anyLong(), any())).thenReturn(CHILD_IDS);
        doThrow(new RuntimeException("boom")).when(gamePurgeService).purgeByChildIds(CHILD_IDS);

        service().purgeDeletedChildrenBatch();

        // 1번이 터져도 2번까지 시도되어야 한다.
        verify(childPurgeService, org.mockito.Mockito.times(2))
                .findDeletedChildIds(anyLong(), any());
    }

    @Test
    void purgeBatch_passesConfiguredBatchSize() {
        when(authPurgeService.findWithdrawnParentIdsForPurge(any(), anyInt()))
                .thenReturn(List.of());

        service(Duration.ofDays(30), 7).purgeBatch();

        verify(authPurgeService).findWithdrawnParentIdsForPurge(any(), org.mockito.Mockito.eq(7));
    }
}
