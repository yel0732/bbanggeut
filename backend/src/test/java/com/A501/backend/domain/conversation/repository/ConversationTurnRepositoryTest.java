package com.A501.backend.domain.conversation.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import com.A501.backend.config.JpaAuditingConfig;
import com.A501.backend.domain.conversation.entity.ConversationTurn;
import com.A501.backend.domain.conversation.entity.Speaker;
import com.A501.backend.domain.conversation.entity.TurnStatus;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class ConversationTurnRepositoryTest {

    @Autowired
    private ConversationTurnRepository conversationTurnRepository;

    @Test
    void 한_대화의_턴을_순서대로_저장하고_조회할_수_있다() {
        ConversationTurn aiTurn = ConversationTurn.create(
                "turn_001", "conv_test_001", 1, Speaker.AI,
                "오늘 게임에서 뭐가 제일 재미있었어?", TurnStatus.COMPLETED, "resp_001", null);
        ConversationTurn childTurn = ConversationTurn.create(
                "turn_002", "conv_test_001", 2, Speaker.CHILD,
                "과일이 많이 나왔을 때 재미있었어.", TurnStatus.COMPLETED, null, new BigDecimal("0.9100"));

        conversationTurnRepository.saveAndFlush(aiTurn);
        conversationTurnRepository.saveAndFlush(childTurn);

        Optional<ConversationTurn> foundAi = conversationTurnRepository.findById("turn_001");
        Optional<ConversationTurn> foundChild = conversationTurnRepository.findById("turn_002");

        assertThat(foundAi).isPresent();
        assertThat(foundAi.get().getSpeaker()).isEqualTo(Speaker.AI);
        assertThat(foundAi.get().getResponseId()).isEqualTo("resp_001");
        assertThat(foundAi.get().getSttConfidence()).isNull();
        assertThat(foundAi.get().getCreatedAt()).isNotNull();

        assertThat(foundChild).isPresent();
        assertThat(foundChild.get().getSpeaker()).isEqualTo(Speaker.CHILD);
        assertThat(foundChild.get().getResponseId()).isNull();
        assertThat(foundChild.get().getSttConfidence()).isEqualByComparingTo("0.9100");
        assertThat(foundChild.get().getTurnSeq()).isEqualTo(2);
    }

    @Test
    void 같은_대화에서_turn_seq가_중복되면_저장에_실패한다() {
        ConversationTurn first = ConversationTurn.create(
                "turn_010", "conv_test_002", 1, Speaker.AI, "질문", TurnStatus.COMPLETED, "resp_010", null);
        conversationTurnRepository.saveAndFlush(first);

        ConversationTurn duplicateSeq = ConversationTurn.create(
                "turn_011", "conv_test_002", 1, Speaker.CHILD, "답변", TurnStatus.COMPLETED, null, new BigDecimal("0.8000"));

        assertThatThrownBy(() -> conversationTurnRepository.saveAndFlush(duplicateSeq))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 중단된_턴은_interruption_정보와_함께_저장된다() {
        LocalDateTime interruptedAt = LocalDateTime.of(2026, 7, 22, 10, 2, 10, 350_000_000);
        ConversationTurn interrupted = ConversationTurn.createInterrupted(
                "turn_020", "conv_test_003", 1, Speaker.AI, "그때 어떤 기분이...",
                "resp_020", interruptedAt, "CHILD_BARGE_IN", "int_001");

        conversationTurnRepository.saveAndFlush(interrupted);
        Optional<ConversationTurn> found = conversationTurnRepository.findById("turn_020");

        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(TurnStatus.INTERRUPTED);
        assertThat(found.get().getInterruptedAt()).isEqualTo(interruptedAt);
        assertThat(found.get().getInterruptionReason()).isEqualTo("CHILD_BARGE_IN");
        assertThat(found.get().getInterruptionId()).isEqualTo("int_001");
    }

    @Test
    void conversation_id가_없으면_저장에_실패한다() {
        ConversationTurn missingConversationId = ConversationTurn.create(
                "turn_030", null, 1, Speaker.AI, "질문", TurnStatus.COMPLETED, "resp_030", null);

        assertThatThrownBy(() -> conversationTurnRepository.saveAndFlush(missingConversationId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void create로_INTERRUPTED_상태를_만들_수_없다() {
        assertThatThrownBy(() -> ConversationTurn.create(
                        "turn_040", "conv_test_004", 1, Speaker.AI, "질문", TurnStatus.INTERRUPTED, "resp_040", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void AI_턴은_responseId가_없으면_생성에_실패한다() {
        assertThatThrownBy(() -> ConversationTurn.create(
                        "turn_041", "conv_test_004", 1, Speaker.AI, "질문", TurnStatus.COMPLETED, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void CHILD가_아닌_턴은_stt_confidence를_가질_수_없다() {
        assertThatThrownBy(() -> ConversationTurn.create(
                        "turn_042", "conv_test_004", 1, Speaker.AI, "질문", TurnStatus.COMPLETED,
                        "resp_042", new BigDecimal("0.9000")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createInterrupted는_AI가_아니거나_responseId가_없으면_실패한다() {
        LocalDateTime now = LocalDateTime.now();

        assertThatThrownBy(() -> ConversationTurn.createInterrupted(
                        "turn_050", "conv_test_005", 1, Speaker.CHILD, "질문", "resp_050", now, "REASON", "int_050"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> ConversationTurn.createInterrupted(
                        "turn_051", "conv_test_005", 1, Speaker.AI, "질문", null, now, "REASON", "int_051"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createInterrupted는_interruptedAt이_없으면_실패한다() {
        assertThatThrownBy(() -> ConversationTurn.createInterrupted(
                        "turn_052", "conv_test_005", 1, Speaker.AI, "질문", "resp_052", null, "REASON", "int_052"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
