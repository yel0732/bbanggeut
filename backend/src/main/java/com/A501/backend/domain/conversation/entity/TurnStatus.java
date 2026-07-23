package com.A501.backend.domain.conversation.entity;

public enum TurnStatus {
    GENERATING,
    COMPLETED,
    INTERRUPTED,
    FAILED;

    public boolean canTransitionTo(TurnStatus nextStatus) {
        if (nextStatus == null) {
            return false;
        }

        return this == GENERATING
                && (nextStatus == COMPLETED || nextStatus == INTERRUPTED || nextStatus == FAILED);
    }
}
