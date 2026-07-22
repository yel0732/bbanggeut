package com.A501.backend.domain.conversation.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import com.A501.backend.config.JpaAuditingConfig;
import com.A501.backend.domain.conversation.entity.ContextType;
import com.A501.backend.domain.conversation.entity.ConversationGoal;
import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationStatus;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class ConversationSessionRepositoryTest {

    @Autowired
    private ConversationSessionRepository conversationSessionRepository;

    @Test
    void 세션을_저장하고_조회하면_모든_필드가_그대로_유지된다() {
        ConversationSession session = ConversationSession.create(
                "conv_test_001", 15L, 152L, ContextType.GAME, ConversationGoal.DIARY, 6);

        conversationSessionRepository.saveAndFlush(session);

        Optional<ConversationSession> found = conversationSessionRepository.findById("conv_test_001");

        assertThat(found).isPresent();
        assertThat(found.get().getChildId()).isEqualTo(15L);
        assertThat(found.get().getGameSessionId()).isEqualTo(152L);
        assertThat(found.get().getStatus()).isEqualTo(ConversationStatus.READY);
        assertThat(found.get().getContextType()).isEqualTo(ContextType.GAME);
        assertThat(found.get().getConversationGoal()).isEqualTo(ConversationGoal.DIARY);
        assertThat(found.get().getQuestionCount()).isZero();
        assertThat(found.get().getMaxQuestionCount()).isEqualTo(6);
        assertThat(found.get().getCurrentSessionEpoch()).isZero();
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(found.get().getUpdatedAt()).isNotNull();
    }

    @Test
    void game_session_id가_없어도_저장된다() {
        ConversationSession session = ConversationSession.create(
                "conv_test_002", 15L, null, ContextType.DAILY, ConversationGoal.DIARY, 6);

        conversationSessionRepository.saveAndFlush(session);

        Optional<ConversationSession> found = conversationSessionRepository.findById("conv_test_002");

        assertThat(found).isPresent();
        assertThat(found.get().getGameSessionId()).isNull();
        assertThat(found.get().getStartedAt()).isNull();
        assertThat(found.get().getCompletedAt()).isNull();
        assertThat(found.get().getTerminationReason()).isNull();
    }

    @Test
    void child_id가_없으면_저장에_실패한다() {
        ConversationSession session = ConversationSession.create(
                "conv_test_003", null, null, ContextType.GAME, ConversationGoal.DIARY, 6);

        assertThatThrownBy(() -> conversationSessionRepository.saveAndFlush(session))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
