package com.A501.backend.domain.conversation.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.A501.backend.config.JpaAuditingConfig;
import com.A501.backend.domain.conversation.entity.ContextType;
import com.A501.backend.domain.conversation.entity.ConversationGoal;
import com.A501.backend.domain.conversation.entity.ConversationSession;
import com.A501.backend.domain.conversation.entity.ConversationStatus;
import com.A501.backend.domain.conversation.entity.ConversationTopic;
import com.A501.backend.domain.conversation.entity.TerminationReason;

import jakarta.persistence.EntityManager;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class ConversationSessionRepositoryTest {

    @Autowired
    private ConversationSessionRepository conversationSessionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 세션을_저장하고_조회하면_모든_필드가_그대로_유지된다() {
        ConversationSession session = ConversationSession.create(
                "conv_test_001",
                15L,
                ConversationTopic.GAME,
                12);

        conversationSessionRepository.saveAndFlush(session);

        Optional<ConversationSession> found = conversationSessionRepository.findById("conv_test_001");

        assertThat(found).isPresent();
        assertThat(found.get().getChildId()).isEqualTo(15L);
        assertThat(found.get().getStatus()).isEqualTo(ConversationStatus.READY);
        assertThat(found.get().getCurrentTopic()).isEqualTo(ConversationTopic.GAME);
        assertThat(found.get().getQuestionCount()).isZero();
        assertThat(found.get().getMaxQuestionCount()).isEqualTo(12);
        assertThat(found.get().getCurrentSessionEpoch()).isZero();
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(found.get().getUpdatedAt()).isNotNull();
    }

    @Test
    void DAILY_세션은_선택_주제와_종료_정보를_저장한다() {
        ConversationSession session = ConversationSession.create(
                "conv_test_002",
                15L,
                ConversationTopic.DAILY,
                6);

        conversationSessionRepository.saveAndFlush(session);

        Optional<ConversationSession> found = conversationSessionRepository.findById("conv_test_002");

        assertThat(found).isPresent();
        assertThat(found.get().getCurrentTopic()).isEqualTo(ConversationTopic.DAILY);
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

    @Test
    void READY와_IN_PROGRESS_세션은_활성_세션으로_조회한다() {
        ConversationSession ready = ConversationSession.create(
                "conv_active_001", 15L, null, ContextType.DAILY, ConversationGoal.DIARY, 6);
        ConversationSession inProgress = ConversationSession.create(
                "conv_active_002", 16L, null, ContextType.DAILY, ConversationGoal.DIARY, 6);
        inProgress.start();
        conversationSessionRepository.saveAllAndFlush(java.util.List.of(ready, inProgress));

        Set<ConversationStatus> activeStatuses = Set.of(
                ConversationStatus.READY, ConversationStatus.IN_PROGRESS);

        assertThat(conversationSessionRepository.existsByChildIdAndStatusIn(15L, activeStatuses)).isTrue();
        assertThat(conversationSessionRepository.existsByChildIdAndStatusIn(16L, activeStatuses)).isTrue();
        assertThat(conversationSessionRepository.existsByChildIdAndStatusIn(17L, activeStatuses)).isFalse();
    }

    @Test
    void 종료_세션만_있으면_활성_세션으로_조회하지_않는다() {
        ConversationSession completed = ConversationSession.create(
                "conv_terminal_001", 15L, null, ContextType.DAILY, ConversationGoal.DIARY, 6);
        completed.start();
        completed.terminate(LocalDateTime.of(2026, 7, 22, 1, 8), TerminationReason.USER_FINISHED);
        conversationSessionRepository.saveAndFlush(completed);

        assertThat(conversationSessionRepository.existsByChildIdAndStatusIn(
                15L, Set.of(ConversationStatus.READY, ConversationStatus.IN_PROGRESS))).isFalse();
    }

    @Test
    void 종료_사유는_문자열_Enum으로_저장되고_version이_증가한다() {
        ConversationSession session = ConversationSession.create(
                "conv_version_001", 15L, null, ContextType.DAILY, ConversationGoal.DIARY, 6);
        session.start();
        conversationSessionRepository.saveAndFlush(session);
        Long initialVersion = session.getVersion();

        session.terminate(LocalDateTime.of(2026, 7, 22, 1, 8), TerminationReason.USER_FINISHED);
        conversationSessionRepository.saveAndFlush(session);

        assertThat(session.getVersion()).isGreaterThan(initialVersion);
        assertThat(session.getTerminationReason()).isEqualTo(TerminationReason.USER_FINISHED);
    }

    @Test
    void 같은_version의_두_세션을_종료하면_뒤의_flush는_낙관적_락_충돌이_발생한다() {
        ConversationSession original = ConversationSession.create(
                "conv_version_002", 15L, null, ContextType.DAILY, ConversationGoal.DIARY, 6);
        original.start();
        conversationSessionRepository.saveAndFlush(original);
        entityManager.detach(original);

        ConversationSession first = conversationSessionRepository.findById("conv_version_002").orElseThrow();
        entityManager.detach(first);
        ConversationSession second = conversationSessionRepository.findById("conv_version_002").orElseThrow();
        entityManager.detach(second);

        first.terminate(LocalDateTime.of(2026, 7, 22, 1, 8), TerminationReason.USER_FINISHED);
        conversationSessionRepository.saveAndFlush(first);
        entityManager.clear();

        second.terminate(LocalDateTime.of(2026, 7, 22, 1, 9), TerminationReason.SYSTEM_ERROR);

        assertThatThrownBy(() -> conversationSessionRepository.saveAndFlush(second))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
