package com.A501.backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.auth.entity.Parent;
import com.A501.backend.domain.auth.exception.PinErrorCode;
import com.A501.backend.domain.auth.repository.ParentRepository;
import com.A501.backend.domain.auth.security.PinProperties;

@ExtendWith(MockitoExtension.class)
class PinServiceTest {

    private static final PinProperties PIN_PROPERTIES = new PinProperties(5, Duration.ofMinutes(5));

    @Mock
    private ParentRepository parentRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private PinService pinService;

    @BeforeEach
    void setUp() {
        pinService = new PinService(parentRepository, passwordEncoder, PIN_PROPERTIES);
    }

    @Test
    void setPin_setsHashedPinWhenNotAlreadySet() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(passwordEncoder.encode("1234")).thenReturn("hashed-pin");

        pinService.setPin(1L, "1234");

        assertThat(parent.hasPin()).isTrue();
        assertThat(parent.getPinHash()).isEqualTo("hashed-pin");
    }

    @Test
    void setPin_throwsWhenPinAlreadySet() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        parent.setPin("existing-hash");
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> pinService.setPin(1L, "1234"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PinErrorCode.PIN_ALREADY_SET));
    }

    @Test
    void setPin_throwsWhenParentNotFound() {
        when(parentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pinService.setPin(99L, "1234"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void verifyPin_throwsWhenPinNotSet() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> pinService.verifyPin(1L, "1234"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PinErrorCode.PIN_NOT_SET));
    }

    @Test
    void verifyPin_succeedsAndResetsFailuresOnMatch() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        parent.setPin("hashed-pin");
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(passwordEncoder.matches("1234", "hashed-pin")).thenReturn(true);

        pinService.verifyPin(1L, "1234");

        assertThat(parent.getPinFailedAttempts()).isZero();
        assertThat(parent.getPinLockedUntil()).isNull();
    }

    @Test
    void verifyPin_throwsMismatchAndIncrementsFailuresBelowThreshold() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        parent.setPin("hashed-pin");
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(passwordEncoder.matches("wrong", "hashed-pin")).thenReturn(false);

        assertThatThrownBy(() -> pinService.verifyPin(1L, "wrong"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PinErrorCode.PIN_MISMATCH));
        assertThat(parent.getPinFailedAttempts()).isEqualTo(1);
    }

    @Test
    void verifyPin_locksOnFifthConsecutiveMismatch() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        parent.setPin("hashed-pin");
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(passwordEncoder.matches("wrong", "hashed-pin")).thenReturn(false);

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> pinService.verifyPin(1L, "wrong"))
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(PinErrorCode.PIN_MISMATCH));
        }
        assertThatThrownBy(() -> pinService.verifyPin(1L, "wrong"))
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PinErrorCode.PIN_LOCKED));

        assertThat(parent.getPinFailedAttempts()).isEqualTo(5);
        assertThat(parent.getPinLockedUntil()).isNotNull();
    }

    @Test
    void verifyPin_throwsLockedImmediatelyWhileLockActive() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        parent.setPin("hashed-pin");
        parent.registerPinFailure(LocalDateTime.now(), 1, Duration.ofMinutes(5));
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> pinService.verifyPin(1L, "1234"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PinErrorCode.PIN_LOCKED));
    }

    @Test
    void changePin_succeedsWhenCurrentPinMatchesAndConfirmMatches() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        parent.setPin("old-hash");
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(passwordEncoder.matches("1234", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("5678")).thenReturn("new-hash");

        pinService.changePin(1L, "1234", "5678", "5678");

        assertThat(parent.getPinHash()).isEqualTo("new-hash");
    }

    @Test
    void changePin_throwsWhenCurrentPinWrongAndIncrementsFailures() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        parent.setPin("old-hash");
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(passwordEncoder.matches("wrong", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> pinService.changePin(1L, "wrong", "5678", "5678"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PinErrorCode.PIN_MISMATCH));
        assertThat(parent.getPinFailedAttempts()).isEqualTo(1);
        assertThat(parent.getPinHash()).isEqualTo("old-hash");
    }

    @Test
    void changePin_throwsWhenPinNotSet() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> pinService.changePin(1L, "1234", "5678", "5678"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PinErrorCode.PIN_NOT_SET));
    }

    @Test
    void changePin_throwsWhenNewPinConfirmationDoesNotMatch() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        parent.setPin("old-hash");
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(passwordEncoder.matches("1234", "old-hash")).thenReturn(true);

        assertThatThrownBy(() -> pinService.changePin(1L, "1234", "5678", "0000"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PinErrorCode.PIN_CONFIRMATION_MISMATCH));
        assertThat(parent.getPinHash()).isEqualTo("old-hash");
    }
}
