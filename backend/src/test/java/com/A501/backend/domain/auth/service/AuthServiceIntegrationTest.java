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

    // 익명화가 없으면 파기 스케줄러가 row를 지울 때까지 같은 이메일로 재가입할 수 없고,
    // 스케줄러를 켜지 않은 환경에서는 영구 차단된다.
    @Test
    void withdraw_freesTheEmailForImmediateResignup() {
        authService.signup(new SignupRequest("reuse@example.com", "Abcdef1!", LocalDate.of(1990, 1, 1)));
        Long parentId = parentRepository.findByEmail("reuse@example.com").orElseThrow().getParentId();

        authService.withdraw(parentId, null);
        parentRepository.flush();

        authService.signup(new SignupRequest("reuse@example.com", "Abcdef1!", LocalDate.of(1990, 1, 1)));

        Parent resignedUp = parentRepository.findByEmail("reuse@example.com").orElseThrow();
        // 기존 계정을 되살린 것이 아니라 완전히 새 row여야 한다.
        assertThat(resignedUp.getParentId()).isNotEqualTo(parentId);
        assertThat(resignedUp.isDeleted()).isFalse();
    }

    // 탈퇴한 구글 계정도 같은 providerId로 다시 가입할 수 있어야 한다. providerId를 비우지 않으면
    // googleAuth가 탈퇴 계정을 필터로 걸러낸 뒤 신규 생성하다 uk_parent_provider_id에 걸린다.
    @Test
    void withdraw_clearsProviderIdSoTheSameGoogleAccountCanSignUpAgain() {
        Parent google = Parent.createGoogle("g@example.com", "google-sub-1", LocalDate.of(1990, 1, 1));
        google.changeProfileImage("profile-presets/parent-avatar");
        Long parentId = parentRepository.saveAndFlush(google).getParentId();

        authService.withdraw(parentId, null);
        parentRepository.flush();

        assertThat(parentRepository.findByProviderId("google-sub-1")).isEmpty();
        assertThat(parentRepository.findById(parentId).orElseThrow().isDeleted()).isTrue();
    }

    // 스케줄러가 꺼져 있으면 row가 무기한 남는데 익명화 덕에 재가입은 잘 되므로 아무도 이상함을
    // 느끼지 못한다. 그 사이 자격증명과 생년월일이 보존되지 않아야 한다.
    @Test
    void withdraw_scrubsCredentialsAndBirthDateButKeepsPurgeKeys() {
        authService.signup(new SignupRequest("scrub@example.com", "Abcdef1!", LocalDate.of(1990, 1, 1)));
        Long parentId = parentRepository.findByEmail("scrub@example.com").orElseThrow().getParentId();
        pinService.setPin(parentId, "1234");

        authService.withdraw(parentId, "1234");
        parentRepository.flush();

        Parent withdrawn = parentRepository.findById(parentId).orElseThrow();
        assertThat(withdrawn.getPasswordHash()).isNull();
        assertThat(withdrawn.getPinHash()).isNull();
        assertThat(withdrawn.getBirthDate()).isEqualTo(LocalDate.of(1900, 1, 1));
        // 파기가 의존하는 값은 남아 있어야 한다.
        assertThat(withdrawn.getDeletedAt()).isNotNull();
        assertThat(withdrawn.getProfileImageKey()).isNotNull();
    }

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
