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
import com.A501.backend.domain.auth.dto.request.LoginRequest;
import com.A501.backend.domain.auth.dto.request.SignupRequest;
import com.A501.backend.domain.auth.entity.Parent;
import com.A501.backend.domain.auth.exception.AuthErrorCode;
import com.A501.backend.domain.auth.exception.PinErrorCode;
import com.A501.backend.domain.auth.repository.ParentRepository;

@SpringBootTest
@Transactional
class AuthServiceIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private PinService pinService;

    @Autowired
    private ParentRepository parentRepository;

    @Test
    void signup_persistsBirthDateAndAuditingTimestampsInRealAppContext() {
        SignupRequest request = new SignupRequest(
                "integration@example.com", "Abcdef1!", LocalDate.of(2000, 3, 15));

        authService.signup(request);

        Parent saved = parentRepository.findByEmail("integration@example.com").orElseThrow();
        assertThat(saved.getBirthDate()).isEqualTo(LocalDate.of(2000, 3, 15));
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    // 클래스 레벨 @Transactional을 이 테스트에서는 끈다 — refresh() 호출마다 실제로 별도 트랜잭션이
    // 커밋되어야, 재사용 감지 시 "다른 활성 토큰 전부 폐기"가 예외로 인한 롤백에 같이 취소되지
    // 않는지(noRollbackFor)를 실제 HTTP 요청과 동일한 조건으로 검증할 수 있다.
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void refresh_reuseDetection_persistsSiblingTokenRevocationAcrossRealTransactions() {
        SignupRequest signupRequest = new SignupRequest(
                "reuse-guard@example.com", "Abcdef1!", LocalDate.of(2000, 1, 1));
        authService.signup(signupRequest);

        TokenIssueResult loginResult = authService.login(
                new LoginRequest("reuse-guard@example.com", "Abcdef1!", true));
        String rawT1 = loginResult.rawRefreshToken();

        TokenIssueResult rotated = authService.refresh(rawT1);
        String rawT2 = rotated.rawRefreshToken();

        assertThatThrownBy(() -> authService.refresh(rawT1))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.REFRESH_TOKEN_REUSED));

        assertThatThrownBy(() -> authService.refresh(rawT2))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.REFRESH_TOKEN_REUSED));
    }

    // withdraw()가 자신의 트랜잭션 안에서 pinService.verifyPin(...)을 호출하는 조합을 실제 HTTP 요청과
    // 동일한 트랜잭션 조건으로 검증한다. noRollbackFor를 빼먹으면 PIN 실패 카운트 증가가 withdraw의
    // 롤백에 같이 취소되어 몇 번을 틀려도 잠기지 않는다(deleteChild에서 겪은 것과 같은 버그).
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void withdraw_persistsPinFailureCountAcrossRealTransactions() {
        Parent parent = authService.signup(
                new SignupRequest("withdraw-lock@example.com", "Abcdef1!", LocalDate.of(2000, 1, 1)));
        pinService.setPin(parent.getParentId(), "1234");

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> authService.withdraw(parent.getParentId(), "0000"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(PinErrorCode.PIN_MISMATCH));
        }

        Parent reloaded = parentRepository.findById(parent.getParentId()).orElseThrow();
        assertThat(reloaded.getPinFailedAttempts()).isEqualTo(4);

        assertThatThrownBy(() -> authService.withdraw(parent.getParentId(), "0000"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PinErrorCode.PIN_LOCKED));

        Parent lockedParent = parentRepository.findById(parent.getParentId()).orElseThrow();
        assertThat(lockedParent.getPinFailedAttempts()).isEqualTo(5);
        assertThat(lockedParent.isDeleted()).isFalse();
    }
}
