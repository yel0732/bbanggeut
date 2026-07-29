package com.A501.backend.domain.conversation.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;

@ExtendWith(MockitoExtension.class)
class ConversationPurgeServiceTest {

    private static final List<Long> CHILD_IDS = List.of(10L, 11L);

    @Mock
    private ConversationSessionRepository sessionRepository;

    @Mock
    private ConversationContentPurger contentPurger;

    @InjectMocks
    private ConversationPurgeService conversationPurgeService;

    // 세션 행을 먼저 지우면 conversationId를 잃어 하위 테이블(turn·topic_state·game_session)과
    // Redis 아티팩트가 통째로 남는다. 순서가 뒤집히지 않도록 고정한다.
    @Test
    void purgeByChildIds_purgesContentBeforeDeletingSessions() {
        when(sessionRepository.findIdsByChildIds(CHILD_IDS)).thenReturn(List.of("conv-1", "conv-2"));

        conversationPurgeService.purgeByChildIds(CHILD_IDS);

        InOrder order = inOrder(contentPurger, sessionRepository);
        order.verify(contentPurger).purge("conv-1");
        order.verify(contentPurger).purge("conv-2");
        order.verify(sessionRepository).deleteByChildIds(CHILD_IDS);
    }

    // purge()는 DB 삭제를 끝낸 뒤 마지막에 Redis를 정리하므로, 여기 오는 예외는 사실상
    // Redis 장애다. 삼키지 않으면 Redis가 죽어 있는 동안 계정 파기 전체가 매 주기 실패한다
    // (로컬에서 Redis 없이 돌렸을 때 실제로 발생했다). 세션 삭제까지 진행되어야 한다.
    @Test
    void purgeByChildIds_deletesSessionsEvenWhenRealtimeCleanupFails() {
        when(sessionRepository.findIdsByChildIds(CHILD_IDS)).thenReturn(List.of("conv-1", "conv-2"));
        doThrow(new IllegalStateException("redis down")).when(contentPurger).purge("conv-1");

        conversationPurgeService.purgeByChildIds(CHILD_IDS);

        verify(contentPurger).purge("conv-2");
        verify(sessionRepository).deleteByChildIds(CHILD_IDS);
    }

    @Test
    void purgeByChildIds_deletesSessionsWhenEveryRealtimeCleanupFails() {
        when(sessionRepository.findIdsByChildIds(CHILD_IDS)).thenReturn(List.of("conv-1"));
        doThrow(new IllegalStateException("redis down")).when(contentPurger).purge(anyString());

        conversationPurgeService.purgeByChildIds(CHILD_IDS);

        verify(sessionRepository).deleteByChildIds(CHILD_IDS);
    }

    @Test
    void purgeByChildIds_doesNothingWhenNoChildIds() {
        conversationPurgeService.purgeByChildIds(List.of());
        conversationPurgeService.purgeByChildIds(null);

        verifyNoInteractions(sessionRepository, contentPurger);
    }

    @Test
    void purgeByChildIds_doesNotDeleteWhenChildrenHaveNoConversations() {
        when(sessionRepository.findIdsByChildIds(CHILD_IDS)).thenReturn(List.of());

        conversationPurgeService.purgeByChildIds(CHILD_IDS);

        verify(contentPurger, never()).purge(anyString());
        verify(sessionRepository, never()).deleteByChildIds(any());
    }
}
