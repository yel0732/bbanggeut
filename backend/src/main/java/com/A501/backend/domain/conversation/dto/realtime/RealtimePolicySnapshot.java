package com.A501.backend.domain.conversation.dto.realtime;

public record RealtimePolicySnapshot(
        boolean oneQuestionAtATime,
        boolean avoidPressureQuestions) {

    public static RealtimePolicySnapshot defaultPolicy() {
        return new RealtimePolicySnapshot(true, true);
    }
}
