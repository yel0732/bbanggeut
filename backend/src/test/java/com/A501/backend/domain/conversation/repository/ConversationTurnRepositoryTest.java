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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
        LocalDateTime startedAt = LocalDateTime.of(2026, 7, 22, 10, 0);
        ConversationTurn aiTurn = ConversationTurn.createAiGenerating(
                "turn_001", "conv_test_001", 1,
                "오늘 게임에서 뭐가 제일 재미있었어?", "resp_001", startedAt);
        aiTurn.complete(startedAt.plusSeconds(2));
        ConversationTurn childTurn = ConversationTurn.createChildCompleted(
                "turn_002", "conv_test_001", 2,
                "과일이 많이 나왔을 때 재미있었어.", new BigDecimal("0.9100"),
                startedAt.plusSeconds(10), startedAt.plusSeconds(20));

        conversationTurnRepository.saveAndFlush(aiTurn);
        conversationTurnRepository.saveAndFlush(childTurn);

        Optional<ConversationTurn> foundAi = conversationTurnRepository.findById("turn_001");
        Optional<ConversationTurn> foundChild = conversationTurnRepository.findById("turn_002");

        assertThat(foundAi).isPresent();
        assertThat(foundAi.get().getSpeaker()).isEqualTo(Speaker.AI);
        assertThat(foundAi.get().getResponseId()).isEqualTo("resp_001");
        assertThat(foundAi.get().getSttConfidence()).isNull();
        assertThat(foundAi.get().getCompletedAt()).isEqualTo(startedAt.plusSeconds(2));
        assertThat(foundAi.get().getCreatedAt()).isNotNull();

        assertThat(foundChild).isPresent();
        assertThat(foundChild.get().getSpeaker()).isEqualTo(Speaker.CHILD);
        assertThat(foundChild.get().getResponseId()).isNull();
        assertThat(foundChild.get().getSttConfidence()).isEqualByComparingTo("0.9100");
        assertThat(foundChild.get().getTurnSeq()).isEqualTo(2);
        assertThat(foundChild.get().getCompletedAt()).isEqualTo(startedAt.plusSeconds(20));
    }

    @Test
    void 같은_대화에서_turn_seq가_중복되면_저장에_실패한다() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 7, 22, 10, 0);
        ConversationTurn first = ConversationTurn.createAiGenerating(
                "turn_010", "conv_test_002", 1, "질문", "resp_010", startedAt);
        first.complete(startedAt.plusSeconds(2));
        conversationTurnRepository.saveAndFlush(first);

        ConversationTurn duplicateSeq = ConversationTurn.createChildCompleted(
                "turn_011", "conv_test_002", 1, "답변", new BigDecimal("0.8000"),
                startedAt.plusSeconds(3), startedAt.plusSeconds(5));

        assertThatThrownBy(() -> conversationTurnRepository.saveAndFlush(duplicateSeq))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 중단된_턴은_interruption_정보와_함께_저장된다() {
        LocalDateTime interruptedAt = LocalDateTime.of(2026, 7, 22, 10, 2, 10, 350_000_000);
        ConversationTurn interrupted = ConversationTurn.createAiInterrupted(
                "turn_020", "conv_test_003", 1, "그때 어떤 기분이...",
                "resp_020", interruptedAt.minusSeconds(2), interruptedAt,
                "CHILD_BARGE_IN", "int_001");

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
        LocalDateTime startedAt = LocalDateTime.of(2026, 7, 22, 10, 0);
        ConversationTurn missingConversationId = ConversationTurn.createChildCompleted(
                "turn_030", null, 1, "답변", null, startedAt, startedAt.plusSeconds(2));

        assertThatThrownBy(() -> conversationTurnRepository.saveAndFlush(missingConversationId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void AI_생성_턴은_startedAt이_없으면_생성에_실패한다() {
        assertThatThrownBy(() -> ConversationTurn.createAiGenerating(
                        "turn_040", "conv_test_004", 1, "질문", "resp_040", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void AI_턴은_responseId가_없으면_생성에_실패한다() {
        assertThatThrownBy(() -> ConversationTurn.createAiGenerating(
                        "turn_041", "conv_test_004", 1, "질문", null, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 완료_CHILD_턴은_완료_시각이_없으면_생성에_실패한다() {
        LocalDateTime startedAt = LocalDateTime.now();

        assertThatThrownBy(() -> ConversationTurn.createChildCompleted(
                        "turn_042", "conv_test_004", 1, "답변",
                        new BigDecimal("0.9000"), startedAt, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createAiInterrupted는_responseId가_없으면_실패한다() {
        LocalDateTime now = LocalDateTime.now();

        assertThatThrownBy(() -> ConversationTurn.createAiInterrupted(
                        "turn_051", "conv_test_005", 1, "질문", null,
                        now.minusSeconds(1), now, "REASON", "int_051"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createAiInterrupted는_interruptedAt이_없으면_실패한다() {
        assertThatThrownBy(() -> ConversationTurn.createAiInterrupted(
                        "turn_052", "conv_test_005", 1, "질문", "resp_052",
                        LocalDateTime.now(), null, "REASON", "int_052"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 대화별_턴을_turnSeq_오름차순으로_페이지_조회한다() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 7, 22, 1, 0);
        ConversationTurn second = ConversationTurn.createChildCompleted(
                "turn_page_002", "conv_page", 2, "두 번째",
                new BigDecimal("0.9000"), startedAt.plusSeconds(10), startedAt.plusSeconds(12));
        ConversationTurn first = ConversationTurn.createAiGenerating(
                "turn_page_001", "conv_page", 1, "첫 번째", "resp_page_001", startedAt);
        first.complete(startedAt.plusSeconds(2));
        conversationTurnRepository.saveAllAndFlush(java.util.List.of(second, first));

        Page<ConversationTurn> result =
                conversationTurnRepository.findByConversationIdOrderByTurnSeqAsc(
                        "conv_page", PageRequest.of(0, 1));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(ConversationTurn::getTurnSeq)
                .containsExactly(1);
    }

    @Test
    void 화자별_턴만_turnSeq_오름차순으로_조회한다() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 7, 22, 1, 0);
        ConversationTurn aiTurn = ConversationTurn.createAiGenerating(
                "turn_speaker_001", "conv_speaker", 1, "질문", "resp_speaker_001", startedAt);
        aiTurn.complete(startedAt.plusSeconds(2));
        ConversationTurn childTurn = ConversationTurn.createChildCompleted(
                "turn_speaker_002", "conv_speaker", 2, "답변",
                new BigDecimal("0.9100"), startedAt.plusSeconds(3), startedAt.plusSeconds(5));
        conversationTurnRepository.saveAllAndFlush(java.util.List.of(aiTurn, childTurn));

        Page<ConversationTurn> result =
                conversationTurnRepository.findByConversationIdAndSpeakerOrderByTurnSeqAsc(
                        "conv_speaker", Speaker.CHILD, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).extracting(ConversationTurn::getTurnId)
                .containsExactly("turn_speaker_002");
    }
}
