package com.A501.backend.domain.conversation.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}
