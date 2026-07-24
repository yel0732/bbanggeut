package com.A501.backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.auth.dto.request.SignupRequest;
import com.A501.backend.domain.auth.entity.Parent;
import com.A501.backend.domain.auth.exception.PinErrorCode;
import com.A501.backend.domain.auth.repository.ParentRepository;
import com.A501.backend.domain.auth.service.AuthService;

@SpringBootTest
class PinServiceIntegrationTest {

    @Autowired
    private PinService pinService;

    @Autowired
    private AuthService authService;

    @Autowired
    private ParentRepository parentRepository;

    // 각 verifyPin() 호출이 실제로 별도 트랜잭션에서 커밋되어야, 실패 횟수 증가가
    // BusinessException으로 인한 롤백에 같이 취소되지 않는지(noRollbackFor)를
    // 실제 HTTP 요청과 동일한 조건으로 검증할 수 있다. AUTH의 refresh token 재사용
    // 탐지에서 겪은 것과 동일한 종류의 버그를 여기서도 미리 막는다.
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void verifyPin_persistsFailureCountAcrossRealTransactions() {
        Parent parent = authService.signup(
                new SignupRequest("pin-integration@example.com", "Abcdef1!", LocalDate.of(2000, 1, 1)));
        pinService.setPin(parent.getParentId(), "1234");

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> pinService.verifyPin(parent.getParentId(), "0000"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(PinErrorCode.PIN_MISMATCH));
        }

        Parent reloaded = parentRepository.findById(parent.getParentId()).orElseThrow();
        assertThat(reloaded.getPinFailedAttempts()).isEqualTo(4);

        assertThatThrownBy(() -> pinService.verifyPin(parent.getParentId(), "0000"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PinErrorCode.PIN_LOCKED));

        Parent lockedParent = parentRepository.findById(parent.getParentId()).orElseThrow();
        assertThat(lockedParent.getPinFailedAttempts()).isEqualTo(5);
        assertThat(lockedParent.getPinLockedUntil()).isNotNull();

        assertThatThrownBy(() -> pinService.verifyPin(parent.getParentId(), "1234"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PinErrorCode.PIN_LOCKED));
    }

    // changePin()은 verifyPin()을 this.verifyPin(...)으로 self-invocation 호출한다 — Spring
    // 프록시를 거치지 않으므로 verifyPin 자체의 @Transactional(noRollbackFor=...)는 적용되지 않고,
    // changePin의 트랜잭션 경계가 대신 적용된다. changePin에도 noRollbackFor를 빼먹으면 이 테스트가
    // 실패해야 한다 — self-invocation 시나리오를 별도로 검증해둔다.
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void changePin_persistsFailureCountWhenCurrentPinWrong() {
        Parent parent = authService.signup(
                new SignupRequest("pin-change-integration@example.com", "Abcdef1!", LocalDate.of(2000, 1, 1)));
        pinService.setPin(parent.getParentId(), "1234");

        assertThatThrownBy(() -> pinService.changePin(parent.getParentId(), "0000", "5678", "5678"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PinErrorCode.PIN_MISMATCH));

        Parent reloaded = parentRepository.findById(parent.getParentId()).orElseThrow();
        assertThat(reloaded.getPinFailedAttempts()).isEqualTo(1);
    }
}
