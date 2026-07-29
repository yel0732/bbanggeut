package com.A501.backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.auth.entity.Parent;
import com.A501.backend.domain.auth.exception.AuthErrorCode;
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
        // ParentFinder는 목이 아니라 실제 구현을 끼운다 — 소프트 삭제 필터가 실제로 도는지까지
        // 이 테스트가 계속 검증하도록 하기 위함이다.
        pinService = new PinService(new ParentFinder(parentRepository), passwordEncoder, PIN_PROPERTIES);
    }

    @Test
    void hasPin_returnsTrueWhenPinAlreadySet() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        parent.setPin("hashed-pin", LocalDateTime.now());
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));

        assertThat(pinService.hasPin(1L)).isTrue();
    }

    @Test
    void hasPin_returnsFalseWhenPinNotSet() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));

        assertThat(pinService.hasPin(1L)).isFalse();
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
        parent.setPin("existing-hash", LocalDateTime.now());
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
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.PARENT_NOT_FOUND));
    }

    @Test
    void setPin_throwsWhenParentIsSoftDeleted() {
        // 탈퇴(소프트 삭제) API는 아직 없어 엔티티에 별도 setter가 없다 — 필드를 직접 세팅해
        // "이미 삭제된 계정의 access token으로 요청이 들어온" 상황만 재현한다.
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(parent, "deletedAt", LocalDateTime.now());
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> pinService.setPin(1L, "1234"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.PARENT_NOT_FOUND));
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
        parent.setPin("hashed-pin", LocalDateTime.now());
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(passwordEncoder.matches("1234", "hashed-pin")).thenReturn(true);

        pinService.verifyPin(1L, "1234");

        assertThat(parent.getPinFailedAttempts()).isZero();
        assertThat(parent.getPinLockedUntil()).isNull();
    }

    @Test
    void verifyPin_throwsMismatchAndIncrementsFailuresBelowThreshold() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        parent.setPin("hashed-pin", LocalDateTime.now());
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
        parent.setPin("hashed-pin", LocalDateTime.now());
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
        ZoneId kst = ZoneId.of("Asia/Seoul");
        LocalDateTime now = LocalDateTime.now(kst);
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        parent.setPin("hashed-pin", now);
        parent.registerPinFailure(now, 1, Duration.ofMinutes(5));
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> pinService.verifyPin(1L, "1234"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PinErrorCode.PIN_LOCKED));
    }

    @Test
    void changePin_succeedsWhenCurrentPinMatchesAndConfirmMatches() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        parent.setPin("old-hash", LocalDateTime.now());
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(passwordEncoder.matches("1234", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("5678")).thenReturn("new-hash");

        pinService.changePin(1L, "1234", "5678", "5678");

        assertThat(parent.getPinHash()).isEqualTo("new-hash");
    }

    @Test
    void changePin_throwsWhenCurrentPinWrongAndIncrementsFailures() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        parent.setPin("old-hash", LocalDateTime.now());
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
        parent.setPin("old-hash", LocalDateTime.now());
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(passwordEncoder.matches("1234", "old-hash")).thenReturn(true);

        assertThatThrownBy(() -> pinService.changePin(1L, "1234", "5678", "0000"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PinErrorCode.PIN_CONFIRMATION_MISMATCH));
        assertThat(parent.getPinHash()).isEqualTo("old-hash");
    }

    // 잠금 잔여시간과 남은 시도 횟수는 서버만 아는 값이다. 실패 카운트가 계정 단위로 누적되므로
    // 클라이언트가 5회째 실패 시점부터 자체 계산하면 다른 기기·다른 화면에서 이미 실패한 이력을
    // 반영하지 못한다. 아래 세 테스트가 이 값들이 예외에 실려 나가는 것을 고정한다.

    @Test
    void verifyPin_carriesRemainingAttemptsOnMismatch() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        parent.setPin("hashed-pin", LocalDateTime.now());
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(passwordEncoder.matches("wrong", "hashed-pin")).thenReturn(false);

        assertThatThrownBy(() -> pinService.verifyPin(1L, "wrong"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException ex = (BusinessException) e;
                    assertThat(ex.getErrorCode()).isEqualTo(PinErrorCode.PIN_MISMATCH);
                    // 5회 정책에서 1회 실패 → 4회 남음
                    assertThat(ex.getRemainingAttempts()).isEqualTo(4);
                    assertThat(ex.getRetryAfterSeconds()).isNull();
                });
    }

    @Test
    void verifyPin_carriesRetryAfterSecondsWhenLockTriggered() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        parent.setPin("hashed-pin", LocalDateTime.now());
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(passwordEncoder.matches("wrong", "hashed-pin")).thenReturn(false);

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> pinService.verifyPin(1L, "wrong"));
        }

        assertThatThrownBy(() -> pinService.verifyPin(1L, "wrong"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException ex = (BusinessException) e;
                    assertThat(ex.getErrorCode()).isEqualTo(PinErrorCode.PIN_LOCKED);
                    // 방금 잠갔으므로 잠금 시간(5분=300초) 전부가 남아 있다. 올림 처리 때문에
                    // 300을 넘지 않으면서도 0이나 음수가 될 수 없다.
                    assertThat(ex.getRetryAfterSeconds()).isBetween(1, 300);
                    assertThat(ex.getRemainingAttempts()).isNull();
                });
    }

    @Test
    void verifyPin_carriesRemainingLockTimeWhileLockAlreadyActive() {
        ZoneId kst = ZoneId.of("Asia/Seoul");
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        parent.setPin("hashed-pin", LocalDateTime.now(kst));
        // 4분 전에 잠긴 상태 — 다른 기기에서 이미 잠근 뒤 이 기기가 처음 시도하는 상황이다.
        parent.registerPinFailure(LocalDateTime.now(kst).minusMinutes(4), 1, Duration.ofMinutes(5));
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> pinService.verifyPin(1L, "1234"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException ex = (BusinessException) e;
                    assertThat(ex.getErrorCode()).isEqualTo(PinErrorCode.PIN_LOCKED);
                    // 5분 잠금 중 4분이 지났으므로 약 60초가 남는다. 경과 시간이 아니라
                    // 남은 시간을 내려준다는 것이 핵심이다.
                    assertThat(ex.getRetryAfterSeconds()).isBetween(55, 60);
                });
    }

    @Test
    void verifyPin_carriesNoExtraInfoForUnrelatedErrors() {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> pinService.verifyPin(1L, "1234"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException ex = (BusinessException) e;
                    assertThat(ex.getErrorCode()).isEqualTo(PinErrorCode.PIN_NOT_SET);
                    assertThat(ex.getRetryAfterSeconds()).isNull();
                    assertThat(ex.getRemainingAttempts()).isNull();
                });
    }

    // 스펙 4.3절이 별도로 경고하는 지점: 잠금이 자연 만료된 뒤 실패 횟수를 리셋하지 않으면,
    // 만료 후 첫 시도(=1회차)가 틀렸을 뿐인데 이미 5였던 카운트 위에 쌓여 즉시 재잠금(423)된다.
    // 정상 동작은 만료 후 첫 실패는 401(PIN_MISMATCH)이어야 하고 실패 횟수는 1이어야 한다.
    @Test
    void verifyPin_resetsFailureCountAfterLockExpiresBeforeCountingNewFailure() {
        ZoneId kst = ZoneId.of("Asia/Seoul");
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        parent.setPin("hashed-pin", LocalDateTime.now(kst));
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(passwordEncoder.matches("wrong", "hashed-pin")).thenReturn(false);

        LocalDateTime expiredLockStart = LocalDateTime.now(kst).minusMinutes(10);
        for (int i = 0; i < 5; i++) {
            parent.registerPinFailure(expiredLockStart, 5, Duration.ofMinutes(5));
        }
        assertThat(parent.getPinLockedUntil()).isBefore(LocalDateTime.now(kst));

        assertThatThrownBy(() -> pinService.verifyPin(1L, "wrong"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PinErrorCode.PIN_MISMATCH));

        assertThat(parent.getPinFailedAttempts()).isEqualTo(1);
        assertThat(parent.getPinLockedUntil()).isNull();
    }
}
