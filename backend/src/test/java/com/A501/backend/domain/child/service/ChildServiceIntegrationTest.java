package com.A501.backend.domain.child.service;

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
import com.A501.backend.domain.auth.service.PinService;
import com.A501.backend.domain.child.dto.response.ChildResponse;
import com.A501.backend.domain.child.entity.Child;
import com.A501.backend.domain.child.repository.ChildRepository;

@SpringBootTest
class ChildServiceIntegrationTest {

    @Autowired
    private ChildService childService;

    @Autowired
    private AuthService authService;

    @Autowired
    private PinService pinService;

    @Autowired
    private ParentRepository parentRepository;

    @Autowired
    private ChildRepository childRepository;

    // deleteChild()가 자신의 트랜잭션 안에서 pinService.verifyPin(...)을 호출하는 조합을
    // 실제 HTTP 요청과 동일한 트랜잭션 조건(각 호출이 별도 커밋)으로 검증한다. Mockito로
    // PinService를 통째로 mock 처리하는 단위 테스트는 트랜잭션 경계 자체를 타지 않아 이
    // 종류의 버그(실패 카운트 증가가 deleteChild의 롤백에 같이 취소되는 문제)를 잡지 못한다.
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void deleteChild_persistsFailureCountAcrossRealTransactions() {
        Parent parent = authService.signup(
                new SignupRequest("child-delete-lock@example.com", "Abcdef1!", LocalDate.of(2000, 1, 1)));
        pinService.setPin(parent.getParentId(), "1234");
        ChildResponse child = childService.createChild(parent.getParentId(), "chorong", null);

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> childService.deleteChild(parent.getParentId(), child.childId(), "0000"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(PinErrorCode.PIN_MISMATCH));
        }

        Parent reloaded = parentRepository.findById(parent.getParentId()).orElseThrow();
        assertThat(reloaded.getPinFailedAttempts()).isEqualTo(4);

        assertThatThrownBy(() -> childService.deleteChild(parent.getParentId(), child.childId(), "0000"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PinErrorCode.PIN_LOCKED));

        Parent lockedParent = parentRepository.findById(parent.getParentId()).orElseThrow();
        assertThat(lockedParent.getPinFailedAttempts()).isEqualTo(5);
        assertThat(lockedParent.getPinLockedUntil()).isNotNull();

        Child stillAlive = childRepository.findByChildIdAndDeletedAtIsNull(child.childId()).orElseThrow();
        assertThat(stillAlive.isDeleted()).isFalse();
    }
}
