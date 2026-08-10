package com.A501.backend.domain.conversation.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class ConversationTurnStateTest {

    @Test
    void GENERATING에서_COMPLETED로_전환할_수_있다() {
        ConversationTurn turn = createGeneratingAiTurn("turn_state_001");
        LocalDateTime completedAt = turn.getStartedAt().plusSeconds(2);

        turn.complete(completedAt);

        assertThat(turn.getStatus()).isEqualTo(TurnStatus.COMPLETED);
        assertThat(turn.getCompletedAt()).isEqualTo(completedAt);
    }

    @Test
    void GENERATING에서_FAILED로_전환할_수_있다() {
        ConversationTurn turn = createGeneratingAiTurn("turn_state_002");
        LocalDateTime failedAt = turn.getStartedAt().plusSeconds(2);

        turn.fail(failedAt);

        assertThat(turn.getStatus()).isEqualTo(TurnStatus.FAILED);
        assertThat(turn.getCompletedAt()).isEqualTo(failedAt);
    }

    @Test
    void GENERATING_AI_턴을_INTERRUPTED로_전환하면_중단_정보가_함께_기록된다() {
        ConversationTurn turn = createGeneratingAiTurn("turn_state_003");
        LocalDateTime interruptedAt = LocalDateTime.of(2026, 7, 23, 10, 30);

        turn.interrupt(interruptedAt, "CHILD_BARGE_IN", "int_state_001");

        assertThat(turn.getStatus()).isEqualTo(TurnStatus.INTERRUPTED);
        assertThat(turn.getInterruptedAt()).isEqualTo(interruptedAt);
        assertThat(turn.getInterruptionReason()).isEqualTo("CHILD_BARGE_IN");
        assertThat(turn.getInterruptionId()).isEqualTo("int_state_001");
    }

    @Test
    void CHILD_턴은_INTERRUPTED로_전환할_수_없다() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 7, 23, 10, 0);
        ConversationTurn childTurn = ConversationTurn.createChildCompleted(
                "turn_state_004", "conv_state_001", 1,
                "아이 답변", null, startedAt, startedAt.plusSeconds(2));

        assertThatThrownBy(() -> childTurn.interrupt(
                        startedAt.plusSeconds(3), "CHILD_BARGE_IN", "int_state_002"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI 턴");
    }

    @Test
    void interruptedAt이_없으면_INTERRUPTED로_전환할_수_없다() {
        ConversationTurn turn = createGeneratingAiTurn("turn_state_005");

        assertThatThrownBy(() -> turn.interrupt(null, "CHILD_BARGE_IN", "int_state_003"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("중단 시각");
    }

    @Test
    void 완료_시각이_없거나_시작보다_빠르면_COMPLETED로_전환할_수_없다() {
        ConversationTurn turn = createGeneratingAiTurn("turn_state_invalid_complete");

        assertThatThrownBy(() -> turn.complete(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> turn.complete(turn.getStartedAt().minusNanos(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(turn.getStatus()).isEqualTo(TurnStatus.GENERATING);
        assertThat(turn.getCompletedAt()).isNull();
    }

    @Test
    void 종료_상태에서는_다른_상태로_전환할_수_없다() {
        ConversationTurn completed = createGeneratingAiTurn("turn_state_006");
        completed.complete(completed.getStartedAt().plusSeconds(2));
        ConversationTurn interrupted = createGeneratingAiTurn("turn_state_007");
        interrupted.interrupt(
                interrupted.getStartedAt().plusSeconds(2), "CHILD_BARGE_IN", "int_state_004");
        ConversationTurn failed = createGeneratingAiTurn("turn_state_008");
        failed.fail(failed.getStartedAt().plusSeconds(2));

        assertThatThrownBy(() -> completed.fail(completed.getStartedAt().plusSeconds(3)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COMPLETED")
                .hasMessageContaining("FAILED");
        assertThatThrownBy(() -> interrupted.complete(interrupted.getStartedAt().plusSeconds(3)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> failed.complete(failed.getStartedAt().plusSeconds(3)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 같은_상태로_다시_전환하면_no_op이다() {
        ConversationTurn completed = createGeneratingAiTurn("turn_state_009");
        LocalDateTime firstCompletedAt = completed.getStartedAt().plusSeconds(2);
        completed.complete(firstCompletedAt);
        completed.complete(completed.getStartedAt().plusSeconds(3));

        ConversationTurn interrupted = createGeneratingAiTurn("turn_state_010");
        LocalDateTime firstInterruptedAt = LocalDateTime.of(2026, 7, 23, 11, 0);
        interrupted.interrupt(firstInterruptedAt, "FIRST_REASON", "int_state_005");
        interrupted.interrupt(
                firstInterruptedAt, "FIRST_REASON", "int_state_005");

        assertThat(completed.getStatus()).isEqualTo(TurnStatus.COMPLETED);
        assertThat(completed.getCompletedAt()).isEqualTo(firstCompletedAt);
        assertThat(interrupted.getInterruptedAt()).isEqualTo(firstInterruptedAt);
        assertThat(interrupted.getInterruptionReason()).isEqualTo("FIRST_REASON");
        assertThat(interrupted.getInterruptionId()).isEqualTo("int_state_005");
    }

    @Test
    void conflictingInterruptionMetadataIsRejected() {
        ConversationTurn interrupted = createGeneratingAiTurn("turn_state_011");
        LocalDateTime firstInterruptedAt = LocalDateTime.of(2026, 7, 23, 11, 0);
        interrupted.interrupt(firstInterruptedAt, "CHILD_BARGE_IN", "int_state_007");

        assertThatThrownBy(() -> interrupted.interrupt(
                        firstInterruptedAt.plusSeconds(1),
                        "USER_REQUESTED",
                        "int_state_008"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("다른 중단 정보");

        assertThat(interrupted.getInterruptedAt()).isEqualTo(firstInterruptedAt);
        assertThat(interrupted.getInterruptionReason()).isEqualTo("CHILD_BARGE_IN");
        assertThat(interrupted.getInterruptionId()).isEqualTo("int_state_007");
    }

    @Test
    void interruptionMetadataIsRequired() {
        ConversationTurn turn = createGeneratingAiTurn("turn_state_012");
        LocalDateTime interruptedAt = turn.getStartedAt().plusSeconds(1);

        assertThatThrownBy(() -> turn.interrupt(
                        interruptedAt,
                        " ",
                        "int_state_009"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> turn.interrupt(
                        interruptedAt,
                        "CHILD_BARGE_IN",
                        null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(turn.getStatus()).isEqualTo(TurnStatus.GENERATING);
    }

    private ConversationTurn createGeneratingAiTurn(String turnId) {
        return ConversationTurn.createAiGenerating(
                turnId,
                "conv_state_001",
                1,
                "AI 질문",
                "resp_" + turnId,
                LocalDateTime.of(2026, 7, 23, 10, 0));
    }
}
