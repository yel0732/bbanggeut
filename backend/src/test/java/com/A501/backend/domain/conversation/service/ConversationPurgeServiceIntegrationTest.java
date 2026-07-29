package com.A501.backend.domain.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationTopic;
import com.A501.backend.domain.conversation.repository.ConversationSessionRepository;

@SpringBootTest
@Transactional
class ConversationPurgeServiceIntegrationTest {

    @Autowired
    private ConversationPurgeService conversationPurgeService;

    @Autowired
    private ConversationSessionRepository sessionRepository;

    // Redis가 없는 환경에서도 돌아야 한다 — 검증 대상은 DB 삭제다.
    @MockitoBean
    private RealtimeContextStore realtimeContextStore;

    @MockitoBean
    private RealtimeSessionLeaseStore realtimeSessionLeaseStore;

    @Test
    void purgeByChildIds_removesSessionsOfTargetChildrenOnly() {
        seed("conv-target-1", 100L);
        seed("conv-target-2", 100L);
        seed("conv-other", 200L);

        conversationPurgeService.purgeByChildIds(List.of(100L));

        assertThat(sessionRepository.findIdsByChildIds(List.of(100L))).isEmpty();
        assertThat(sessionRepository.findIdsByChildIds(List.of(200L))).containsExactly("conv-other");
    }

    @Test
    void purgeByChildIds_isIdempotent() {
        seed("conv-twice", 300L);

        conversationPurgeService.purgeByChildIds(List.of(300L));
        conversationPurgeService.purgeByChildIds(List.of(300L));

        assertThat(sessionRepository.findIdsByChildIds(List.of(300L))).isEmpty();
    }

    @Test
    void purgeByChildIds_succeedsWhenChildHasNoConversations() {
        conversationPurgeService.purgeByChildIds(List.of(999L));

        assertThat(sessionRepository.findIdsByChildIds(List.of(999L))).isEmpty();
    }

    private void seed(String conversationId, Long childId) {
        sessionRepository.saveAndFlush(
                ConversationSession.create(conversationId, childId, ConversationTopic.DAILY, 6));
    }
}
