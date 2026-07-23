package com.A501.backend.domain.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.A501.backend.domain.conversation.exception.ConversationErrorCode;
import com.A501.backend.domain.conversation.exception.ConversationException;

class LocalChildAccessVerifierTest {

    private final LocalChildAccessVerifier verifier = new LocalChildAccessVerifier(15L, 1L);

    @Test
    void Mock_사용자는_설정된_아이에_접근할_수_있다() {
        assertThatCode(() -> verifier.verifyAccess(1L, 15L)).doesNotThrowAnyException();
    }

    @Test
    void 설정되지_않은_아이는_존재하지_않는_것으로_처리한다() {
        assertError(1L, 99L, ConversationErrorCode.CHILD_NOT_FOUND);
    }

    @Test
    void Mock_아이의_보호자가_아니면_접근을_거절한다() {
        assertError(2L, 15L, ConversationErrorCode.CHILD_ACCESS_DENIED);
    }

    private void assertError(Long memberId, Long childId, ConversationErrorCode expected) {
        assertThatThrownBy(() -> verifier.verifyAccess(memberId, childId))
                .isInstanceOfSatisfying(ConversationException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
