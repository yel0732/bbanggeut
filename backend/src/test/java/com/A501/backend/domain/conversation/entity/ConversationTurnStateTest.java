package com.A501.backend.domain.conversation.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class ConversationTurnStateTest {

    @Test
    void GENERATING에서_COMPLETED로_전환할_수_있다() {
        ConversationTurn turn = createGeneratingAiTurn("turn_state_001");

        turn.complete();

        assertThat(turn.getStatus()).isEqualTo(TurnStatus.COMPLETED);
    }

    @Test
    void GENERATING에서_FAILED로_전환할_수_있다() {
        ConversationTurn turn = createGeneratingAiTurn("turn_state_002");

        turn.fail();

        assertThat(turn.getStatus()).isEqualTo(TurnStatus.FAILED);
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
        ConversationTurn childTurn = ConversationTurn.create(
                "turn_state_004", "conv_state_001", 1, Speaker.CHILD,
                "아이 답변", TurnStatus.GENERATING, null, null);

        assertThatThrownBy(() -> childTurn.interrupt(
                        LocalDateTime.now(), "CHILD_BARGE_IN", "int_state_002"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI 턴");
    }

    @Test
    void interruptedAt이_없으면_INTERRUPTED로_전환할_수_없다() {
        ConversationTurn turn = createGeneratingAiTurn("turn_state_005");

        assertThatThrownBy(() -> turn.interrupt(null, "CHILD_BARGE_IN", "int_state_003"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("interruptedAt");
    }

    @Test
    void 종료_상태에서는_다른_상태로_전환할_수_없다() {
        ConversationTurn completed = createGeneratingAiTurn("turn_state_006");
        completed.complete();
        ConversationTurn interrupted = createGeneratingAiTurn("turn_state_007");
        interrupted.interrupt(LocalDateTime.now(), "CHILD_BARGE_IN", "int_state_004");
        ConversationTurn failed = createGeneratingAiTurn("turn_state_008");
        failed.fail();

        assertThatThrownBy(completed::fail)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COMPLETED")
                .hasMessageContaining("FAILED");
        assertThatThrownBy(interrupted::complete).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(failed::complete).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 같은_상태로_다시_전환하면_no_op이다() {
        ConversationTurn completed = createGeneratingAiTurn("turn_state_009");
        completed.complete();
        completed.complete();

        ConversationTurn interrupted = createGeneratingAiTurn("turn_state_010");
        LocalDateTime firstInterruptedAt = LocalDateTime.of(2026, 7, 23, 11, 0);
        interrupted.interrupt(firstInterruptedAt, "FIRST_REASON", "int_state_005");
        interrupted.interrupt(
                LocalDateTime.of(2026, 7, 23, 12, 0), "SECOND_REASON", "int_state_006");

        assertThat(completed.getStatus()).isEqualTo(TurnStatus.COMPLETED);
        assertThat(interrupted.getInterruptedAt()).isEqualTo(firstInterruptedAt);
        assertThat(interrupted.getInterruptionReason()).isEqualTo("FIRST_REASON");
        assertThat(interrupted.getInterruptionId()).isEqualTo("int_state_005");
    }

    private ConversationTurn createGeneratingAiTurn(String turnId) {
        return ConversationTurn.create(
                turnId, "conv_state_001", 1, Speaker.AI,
                "AI 질문", TurnStatus.GENERATING, "resp_" + turnId, null);
    }
}
