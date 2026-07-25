package com.A501.backend.domain.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class ParentTest {

    @Test
    void hasPin_returnsFalseBeforeSet() {
        Parent parent = createParent();

        assertThat(parent.hasPin()).isFalse();
    }

    @Test
    void setPin_marksPinAsSetAndClearsFailures() {
        Parent parent = createParent();

        parent.setPin("hashed-pin");

        assertThat(parent.hasPin()).isTrue();
        assertThat(parent.getPinSetAt()).isNotNull();
        assertThat(parent.getPinFailedAttempts()).isZero();
        assertThat(parent.getPinLockedUntil()).isNull();
    }

    @Test
    void isPinLocked_falseWhenNeverLocked() {
        Parent parent = createParent();

        assertThat(parent.isPinLocked(LocalDateTime.now())).isFalse();
    }

    @Test
    void registerPinFailure_incrementsWithoutLockingBelowThreshold() {
        Parent parent = createParent();
        LocalDateTime now = LocalDateTime.now();

        parent.registerPinFailure(now, 5, Duration.ofMinutes(5));
        parent.registerPinFailure(now, 5, Duration.ofMinutes(5));

        assertThat(parent.getPinFailedAttempts()).isEqualTo(2);
        assertThat(parent.isPinLocked(now)).isFalse();
    }

    @Test
    void registerPinFailure_locksOnceThresholdReached() {
        Parent parent = createParent();
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < 5; i++) {
            parent.registerPinFailure(now, 5, Duration.ofMinutes(5));
        }

        assertThat(parent.getPinFailedAttempts()).isEqualTo(5);
        assertThat(parent.isPinLocked(now)).isTrue();
        assertThat(parent.getPinLockedUntil()).isEqualTo(now.plusMinutes(5));
    }

    @Test
    void isPinLocked_falseAfterLockWindowPasses() {
        Parent parent = createParent();
        LocalDateTime lockedAt = LocalDateTime.now();
        for (int i = 0; i < 5; i++) {
            parent.registerPinFailure(lockedAt, 5, Duration.ofMinutes(5));
        }

        assertThat(parent.isPinLocked(lockedAt.plusMinutes(6))).isFalse();
    }

    @Test
    void resetPinFailuresIfLockExpired_resetsWhenLockHasPassed() {
        Parent parent = createParent();
        LocalDateTime lockedAt = LocalDateTime.now();
        for (int i = 0; i < 5; i++) {
            parent.registerPinFailure(lockedAt, 5, Duration.ofMinutes(5));
        }

        parent.resetPinFailuresIfLockExpired(lockedAt.plusMinutes(6));

        assertThat(parent.getPinFailedAttempts()).isZero();
        assertThat(parent.getPinLockedUntil()).isNull();
    }

    @Test
    void resetPinFailuresIfLockExpired_doesNothingWhileStillLocked() {
        Parent parent = createParent();
        LocalDateTime lockedAt = LocalDateTime.now();
        for (int i = 0; i < 5; i++) {
            parent.registerPinFailure(lockedAt, 5, Duration.ofMinutes(5));
        }

        parent.resetPinFailuresIfLockExpired(lockedAt.plusMinutes(1));

        assertThat(parent.getPinFailedAttempts()).isEqualTo(5);
        assertThat(parent.getPinLockedUntil()).isNotNull();
    }

    @Test
    void resetPinFailuresIfLockExpired_doesNothingWhenNeverLocked() {
        Parent parent = createParent();

        parent.resetPinFailuresIfLockExpired(LocalDateTime.now());

        assertThat(parent.getPinFailedAttempts()).isZero();
    }

    private Parent createParent() {
        return Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
    }
}
