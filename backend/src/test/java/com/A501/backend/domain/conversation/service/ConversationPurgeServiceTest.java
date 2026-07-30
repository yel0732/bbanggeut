package com.A501.backend.domain.conversation.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;
import com.A501.backend.domain.conversation.repository.ConversationEventFailureRepository;
import com.A501.backend.domain.conversation.repository.ConversationEventRepository;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;

@ExtendWith(MockitoExtension.class)
class ConversationPurgeServiceTest {

    @Mock
    private ConversationSessionRepository sessionRepository;
    @Mock
    private ConversationEventRepository eventRepository;
    @Mock
    private ConversationEventFailureRepository eventFailureRepository;
    @Mock
    private ConversationContentPurger contentPurger;

    @InjectMocks
    private ConversationPurgeService purgeService;

    @Test
    void skipsPurgeWhenChildIdsAreEmpty() {
        purgeService.purgeByChildIds(List.of());

        verifyNoInteractions(
                sessionRepository,
                eventRepository,
                eventFailureRepository,
                contentPurger);
    }

    @Test
    void deletesConversationRowsBeforeSessions() {
        List<Long> childIds = List.of(10L);
        List<String> conversationIds = List.of("conv-1", "conv-2");
        when(sessionRepository.findIdsByChildIds(childIds)).thenReturn(conversationIds);

        purgeService.purgeByChildIds(childIds);

        InOrder order = inOrder(
                contentPurger,
                eventFailureRepository,
                eventRepository,
                sessionRepository);
        order.verify(contentPurger).purge("conv-1");
        order.verify(contentPurger).purge("conv-2");
        order.verify(eventFailureRepository).deleteByConversationIds(conversationIds);
        order.verify(eventRepository).deleteByConversationIds(conversationIds);
        order.verify(sessionRepository).deleteByConversationIds(conversationIds);
    }

    // Redis가 죽어 있으면 purge()가 REDIS_UNAVAILABLE을 던진다. 이걸 흘려보내면 자녀 프로필
    // 삭제가 트랜잭션째로 롤백되어(동기 호출) 부모가 PIN을 맞게 넣었는데도 아무것도 지워지지
    // 않고, 계정 파기(스케줄러)에서는 매 주기 실패가 반복된다. 로컬에서 실제로 발생했다.
    @Test
    void deletesEverythingEvenWhenRealtimeCleanupIsUnavailable() {
        List<Long> childIds = List.of(10L);
        List<String> conversationIds = List.of("conv-1", "conv-2");
        when(sessionRepository.findIdsByChildIds(childIds)).thenReturn(conversationIds);
        doThrow(new ConversationException(ConversationErrorCode.REDIS_UNAVAILABLE))
                .when(contentPurger).purge("conv-1");

        purgeService.purgeByChildIds(childIds);

        verify(contentPurger).purge("conv-2");
        verify(eventFailureRepository).deleteByConversationIds(conversationIds);
        verify(eventRepository).deleteByConversationIds(conversationIds);
        verify(sessionRepository).deleteByConversationIds(conversationIds);
    }

    // DB 오류는 삼키면 안 된다. 넘기면 대화 내용이 남은 채 세션 행만 지워져 고아가 된다.
    @Test
    void propagatesFailuresThatAreNotRedisOutages() {
        List<Long> childIds = List.of(10L);
        when(sessionRepository.findIdsByChildIds(childIds)).thenReturn(List.of("conv-1"));
        doThrow(new ConversationException(ConversationErrorCode.CONVERSATION_NOT_FOUND))
                .when(contentPurger).purge("conv-1");

        assertThatThrownBy(() -> purgeService.purgeByChildIds(childIds))
                .isInstanceOf(ConversationException.class);

        verify(sessionRepository, never()).deleteByConversationIds(any());
    }
}
