package com.A501.backend.domain.conversation.entity;

public enum ConversationStatus {
    READY,
    IN_PROGRESS,
    COMPLETED,
    ABORTED,
    EXPIRED,
    FAILED;

    public boolean isTerminal() {
        return switch (this) {
            case COMPLETED, ABORTED, EXPIRED, FAILED -> true;
            case READY, IN_PROGRESS -> false;
        };
    }

    public boolean canTransitionTo(ConversationStatus nextStatus) {
        if (nextStatus == null) {
            return false;
        }

        return switch (this) {
            case READY -> nextStatus == IN_PROGRESS
                    || nextStatus == ABORTED
                    || nextStatus == EXPIRED;
            case IN_PROGRESS -> nextStatus == COMPLETED
                    || nextStatus == ABORTED
                    || nextStatus == EXPIRED
                    || nextStatus == FAILED;
            case COMPLETED, ABORTED, EXPIRED, FAILED -> false;
        };
    }
}
