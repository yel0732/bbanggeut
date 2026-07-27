package com.A501.backend.domain.conversation.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class ConversationSessionStateTest {

    private static final LocalDateTime COMPLETED_AT = LocalDateTime.of(2026, 7, 22, 1, 8);

    @Test
    void 새_세션은_READY_상태다() {
        ConversationSession session = createSession("conv_state_001");

        assertThat(session.getStatus()).isEqualTo(ConversationStatus.READY);
        assertThat(session.getCompletedAt()).isNull();
        assertThat(session.getTerminationReason()).isNull();
    }

    @Test
    void READY에서_IN_PROGRESS로_전환할_수_있다() {
        ConversationSession session = createSession("conv_state_002");

        session.start();

        assertThat(session.getStatus()).isEqualTo(ConversationStatus.IN_PROGRESS);
    }

    @Test
    void 종료_사유에_따라_terminal_상태와_생명주기를_함께_기록한다() {
        assertTermination(TerminationReason.USER_FINISHED, ConversationStatus.COMPLETED);
        assertTermination(TerminationReason.MAX_QUESTION_REACHED, ConversationStatus.COMPLETED);
        assertTermination(TerminationReason.CHILD_REQUESTED_STOP, ConversationStatus.COMPLETED);
        assertTermination(TerminationReason.REQUIRED_SLOTS_FILLED, ConversationStatus.COMPLETED);
        assertTermination(TerminationReason.INACTIVITY_TIMEOUT, ConversationStatus.EXPIRED);
        assertTermination(TerminationReason.CONNECTION_LOST, ConversationStatus.ABORTED);
        assertTermination(TerminationReason.PAGE_CLOSED, ConversationStatus.ABORTED);
        assertTermination(TerminationReason.SYSTEM_ERROR, ConversationStatus.FAILED);
    }

    @Test
    void READY에서는_ABORTED와_EXPIRED_사유만_종료할_수_있다() {
        ConversationSession aborted = createSession("conv_state_003");
        ConversationSession expired = createSession("conv_state_004");

        aborted.terminate(COMPLETED_AT, TerminationReason.CONNECTION_LOST);
        expired.terminate(COMPLETED_AT, TerminationReason.INACTIVITY_TIMEOUT);

        assertThat(aborted.getStatus()).isEqualTo(ConversationStatus.ABORTED);
        assertThat(expired.getStatus()).isEqualTo(ConversationStatus.EXPIRED);
    }

    @Test
    void READY에서_COMPLETED나_FAILED로_종료하면_일부_필드도_변경하지_않는다() {
        ConversationSession completed = createSession("conv_state_005");
        ConversationSession failed = createSession("conv_state_006");

        assertThatThrownBy(() -> completed.terminate(COMPLETED_AT, TerminationReason.USER_FINISHED))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> failed.terminate(COMPLETED_AT, TerminationReason.SYSTEM_ERROR))
                .isInstanceOf(IllegalStateException.class);
        assertThat(completed.getStatus()).isEqualTo(ConversationStatus.READY);
        assertThat(completed.getCompletedAt()).isNull();
        assertThat(completed.getTerminationReason()).isNull();
        assertThat(failed.getStatus()).isEqualTo(ConversationStatus.READY);
        assertThat(failed.getCompletedAt()).isNull();
        assertThat(failed.getTerminationReason()).isNull();
    }

    @Test
    void 이미_종료된_세션은_다른_사유로_재요청해도_최초_결과를_유지한다() {
        ConversationSession session = startedSession("conv_state_007");
        LocalDateTime firstCompletedAt = COMPLETED_AT;

        session.terminate(firstCompletedAt, TerminationReason.USER_FINISHED);
        session.terminate(firstCompletedAt.plusMinutes(1), TerminationReason.SYSTEM_ERROR);

        assertThat(session.getStatus()).isEqualTo(ConversationStatus.COMPLETED);
        assertThat(session.getCompletedAt()).isEqualTo(firstCompletedAt);
        assertThat(session.getTerminationReason()).isEqualTo(TerminationReason.USER_FINISHED);
    }

    @Test
    void 종료_시각이나_사유가_null이면_상태를_변경하지_않는다() {
        ConversationSession missingTime = startedSession("conv_state_008");
        ConversationSession missingReason = startedSession("conv_state_009");

        assertThatThrownBy(() -> missingTime.terminate(null, TerminationReason.USER_FINISHED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> missingReason.terminate(COMPLETED_AT, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(missingTime.getStatus()).isEqualTo(ConversationStatus.IN_PROGRESS);
        assertThat(missingReason.getStatus()).isEqualTo(ConversationStatus.IN_PROGRESS);
    }

    @Test
    void terminal_상태를_정확히_구분한다() {
        assertThat(ConversationStatus.READY.isTerminal()).isFalse();
        assertThat(ConversationStatus.IN_PROGRESS.isTerminal()).isFalse();
        assertThat(ConversationStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(ConversationStatus.ABORTED.isTerminal()).isTrue();
        assertThat(ConversationStatus.EXPIRED.isTerminal()).isTrue();
        assertThat(ConversationStatus.FAILED.isTerminal()).isTrue();
    }

    @Test
    void 시작_시각과_전달된_질문_수만_명시적으로_기록한다() {
        ConversationSession session = createSession("conv_state_question");
        LocalDateTime startedAt = LocalDateTime.of(2026, 7, 22, 1, 0);

        session.start(startedAt);
        session.incrementQuestionCount();

        assertThat(session.getStartedAt()).isEqualTo(startedAt);
        assertThat(session.getQuestionCount()).isEqualTo(1);
    }

    @Test
    void Realtime_Session_발급마다_currentSessionEpoch를_증가시킨다() {
        ConversationSession session = createSession("conv_state_realtime_epoch");

        int firstEpoch = session.startRealtimeSession();
        int secondEpoch = session.startRealtimeSession();

        assertThat(firstEpoch).isEqualTo(1);
        assertThat(secondEpoch).isEqualTo(2);
        assertThat(session.getCurrentSessionEpoch()).isEqualTo(2);
    }

    @Test
    void READY_상태에서도_Realtime_Session을_발급할_수_있다() {
        ConversationSession session = createSession("conv_state_realtime_ready");

        int epoch = session.startRealtimeSession();

        assertThat(epoch).isEqualTo(1);
        assertThat(session.getStatus()).isEqualTo(ConversationStatus.READY);
    }

    @Test
    void 종료된_세션에는_Realtime_Session을_발급할_수_없다() {
        ConversationSession session = startedSession("conv_state_realtime_terminal");
        session.terminate(COMPLETED_AT, TerminationReason.USER_FINISHED);

        assertThatThrownBy(session::startRealtimeSession)
                .isInstanceOf(IllegalStateException.class);
    }

    private void assertTermination(TerminationReason reason, ConversationStatus expectedStatus) {
        ConversationSession session = startedSession("conv_state_" + reason.name());

        session.terminate(COMPLETED_AT, reason);

        assertThat(session.getStatus()).isEqualTo(expectedStatus);
        assertThat(session.getCompletedAt()).isEqualTo(COMPLETED_AT);
        assertThat(session.getTerminationReason()).isEqualTo(reason);
    }

    private ConversationSession createSession(String conversationId) {
        return ConversationSession.create(
                conversationId, 15L, null, ContextType.DAILY, ConversationGoal.DIARY, 6);
    }

    private ConversationSession startedSession(String conversationId) {
        ConversationSession session = createSession(conversationId);
        session.start();
        return session;
    }
}
