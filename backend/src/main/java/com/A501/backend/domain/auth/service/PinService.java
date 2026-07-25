package com.A501.backend.domain.auth.service;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.auth.entity.Parent;
import com.A501.backend.domain.auth.exception.PinErrorCode;
import com.A501.backend.domain.auth.repository.ParentRepository;
import com.A501.backend.domain.auth.security.PinProperties;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PinService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ParentRepository parentRepository;
    private final PasswordEncoder passwordEncoder;
    private final PinProperties pinProperties;

    @Transactional
    public void setPin(Long parentId, String rawPin) {
        Parent parent = getParent(parentId);
        if (parent.hasPin()) {
            throw new BusinessException(PinErrorCode.PIN_ALREADY_SET);
        }
        parent.setPin(passwordEncoder.encode(rawPin));
    }

    // 잠금 카운트 증가가 BusinessException으로 인한 트랜잭션 롤백에 같이 취소되지 않도록
    // (AUTH의 refresh token 재사용 탐지에서 겪은 것과 동일한 문제) 롤백 대상에서 제외한다.
    @Transactional(noRollbackFor = BusinessException.class)
    public void verifyPin(Long parentId, String rawPin) {
        Parent parent = getParent(parentId);
        if (!parent.hasPin()) {
            throw new BusinessException(PinErrorCode.PIN_NOT_SET);
        }

        LocalDateTime now = LocalDateTime.now(KST);
        if (parent.isPinLocked(now)) {
            throw new BusinessException(PinErrorCode.PIN_LOCKED);
        }
        parent.resetPinFailuresIfLockExpired(now);

        if (!passwordEncoder.matches(rawPin, parent.getPinHash())) {
            parent.registerPinFailure(now, pinProperties.maxFailedAttempts(), pinProperties.lockDuration());
            throw new BusinessException(
                    parent.isPinLocked(now) ? PinErrorCode.PIN_LOCKED : PinErrorCode.PIN_MISMATCH);
        }

        parent.resetPinFailures();
    }

    // verifyPin과 마찬가지 이유로 noRollbackFor가 필요하다 — changePin 안에서 verifyPin을
    // this.verifyPin(...)으로 self-invocation 호출하면 Spring 프록시를 거치지 않아
    // verifyPin 자체의 @Transactional 설정이 적용되지 않고, changePin의 트랜잭션 경계가
    // 실제로 적용된다. 즉 이 메서드에도 동일하게 선언해야 한다.
    @Transactional(noRollbackFor = BusinessException.class)
    public void changePin(Long parentId, String currentPin, String newPin, String newPinConfirm) {
        verifyPin(parentId, currentPin);

        if (!newPin.equals(newPinConfirm)) {
            throw new BusinessException(PinErrorCode.PIN_CONFIRMATION_MISMATCH);
        }

        Parent parent = getParent(parentId);
        parent.setPin(passwordEncoder.encode(newPin));
        // TODO(TBD-P6): 신규 PIN이 기존 PIN과 동일한 경우의 허용/거부 정책 미정 — 지금은 허용.
    }

    private Parent getParent(Long parentId) {
        return parentRepository.findById(parentId)
                .orElseThrow(() -> new IllegalStateException(
                        "인증된 parentId에 해당하는 Parent를 찾을 수 없습니다: " + parentId));
    }
}
