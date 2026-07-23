package com.A501.backend.domain.conversation.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ConversationSessionStateTest {

    @Test
    void 새_세션은_READY_상태다() {
        ConversationSession session = createSession("conv_state_001");

        assertThat(session.getStatus()).isEqualTo(ConversationStatus.READY);
    }

    @Test
    void READY에서_IN_PROGRESS로_전환할_수_있다() {
        ConversationSession session = createSession("conv_state_002");

        session.start();

        assertThat(session.getStatus()).isEqualTo(ConversationStatus.IN_PROGRESS);
    }

    @Test
    void READY에서_ABORTED와_EXPIRED로_전환할_수_있다() {
        ConversationSession aborted = createSession("conv_state_003");
        ConversationSession expired = createSession("conv_state_004");

        aborted.abort();
        expired.expire();

        assertThat(aborted.getStatus()).isEqualTo(ConversationStatus.ABORTED);
        assertThat(expired.getStatus()).isEqualTo(ConversationStatus.EXPIRED);
    }

    @Test
    void IN_PROGRESS에서_모든_허용된_종료_상태로_전환할_수_있다() {
        ConversationSession completed = startedSession("conv_state_005");
        ConversationSession aborted = startedSession("conv_state_006");
        ConversationSession expired = startedSession("conv_state_007");
        ConversationSession failed = startedSession("conv_state_008");

        completed.complete();
        aborted.abort();
        expired.expire();
        failed.fail();

        assertThat(completed.getStatus()).isEqualTo(ConversationStatus.COMPLETED);
        assertThat(aborted.getStatus()).isEqualTo(ConversationStatus.ABORTED);
        assertThat(expired.getStatus()).isEqualTo(ConversationStatus.EXPIRED);
        assertThat(failed.getStatus()).isEqualTo(ConversationStatus.FAILED);
    }

    @Test
    void READY에서_COMPLETED나_FAILED로_바로_전환할_수_없다() {
        ConversationSession completed = createSession("conv_state_009");
        ConversationSession failed = createSession("conv_state_010");

        assertThatThrownBy(completed::complete)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("READY")
                .hasMessageContaining("COMPLETED");
        assertThatThrownBy(failed::fail)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("READY")
                .hasMessageContaining("FAILED");
    }

    @Test
    void 종료_상태에서는_다른_상태로_전환할_수_없다() {
        ConversationSession completed = startedSession("conv_state_011");
        completed.complete();
        ConversationSession aborted = createSession("conv_state_012");
        aborted.abort();
        ConversationSession expired = createSession("conv_state_013");
        expired.expire();
        ConversationSession failed = startedSession("conv_state_014");
        failed.fail();

        assertThatThrownBy(completed::fail).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(aborted::expire).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(expired::abort).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(failed::complete).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 같은_상태로_다시_전환하면_no_op이다() {
        ConversationSession session = startedSession("conv_state_015");

        session.start();
        session.complete();
        session.complete();

        assertThat(session.getStatus()).isEqualTo(ConversationStatus.COMPLETED);
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
