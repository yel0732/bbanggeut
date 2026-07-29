package com.A501.backend.domain.auth.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.auth.entity.Parent;
import com.A501.backend.domain.auth.exception.PinErrorCode;
import com.A501.backend.domain.auth.security.PinProperties;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PinService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ParentFinder parentFinder;
    private final PasswordEncoder passwordEncoder;
    private final PinProperties pinProperties;

    @Transactional
    public void setPin(Long parentId, String rawPin) {
        Parent parent = parentFinder.getActive(parentId);
        if (parent.hasPin()) {
            throw new BusinessException(PinErrorCode.PIN_ALREADY_SET);
        }
        parent.setPin(passwordEncoder.encode(rawPin), LocalDateTime.now(KST));
    }

    // 잠금 카운트 증가가 BusinessException으로 인한 트랜잭션 롤백에 같이 취소되지 않도록
    // (AUTH의 refresh token 재사용 탐지에서 겪은 것과 동일한 문제) 롤백 대상에서 제외한다.
    @Transactional(noRollbackFor = BusinessException.class)
    public void verifyPin(Long parentId, String rawPin) {
        Parent parent = parentFinder.getActive(parentId);
        if (!parent.hasPin()) {
            throw new BusinessException(PinErrorCode.PIN_NOT_SET);
        }

        LocalDateTime now = LocalDateTime.now(KST);
        if (parent.isPinLocked(now)) {
            throw lockedException(parent, now);
        }
        parent.resetPinFailuresIfLockExpired(now);

        if (!passwordEncoder.matches(rawPin, parent.getPinHash())) {
            parent.registerPinFailure(now, pinProperties.maxFailedAttempts(), pinProperties.lockDuration());
            throw parent.isPinLocked(now)
                    ? lockedException(parent, now)
                    : BusinessException.withRemainingAttempts(
                            PinErrorCode.PIN_MISMATCH,
                            pinProperties.maxFailedAttempts() - parent.getPinFailedAttempts());
        }

        parent.resetPinFailures();
    }

    // 잠금 해제까지 남은 초를 올림해서 싣는다. 내림하면(284.7 → 284) 클라이언트 카운트다운이
    // 잠금이 풀리기 직전에 0이 되어 재시도했다가 다시 423을 받는다.
    // now와 pinLockedUntil은 둘 다 KST 기준으로 만들어진 값이라 JVM 기본 타임존과 무관하게 맞는다.
    private BusinessException lockedException(Parent parent, LocalDateTime now) {
        long remainingMillis = Duration.between(now, parent.getPinLockedUntil()).toMillis();
        int retryAfterSeconds = (int) Math.max(1, (remainingMillis + 999) / 1000);
        return BusinessException.retryAfter(PinErrorCode.PIN_LOCKED, retryAfterSeconds);
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

        Parent parent = parentFinder.getActive(parentId);
        parent.setPin(passwordEncoder.encode(newPin), LocalDateTime.now(KST));
        // TODO(TBD-P6): 신규 PIN이 기존 PIN과 동일한 경우의 허용/거부 정책 미정 — 지금은 허용.
    }

    // child 도메인이 Parent 엔티티/리포지토리를 직접 참조하지 않고 이 서비스 메서드를 통해서만
    // PIN 설정 여부를 확인하도록 하기 위한 공개 API.
    public boolean hasPin(Long parentId) {
        return parentFinder.getActive(parentId).hasPin();
    }
}
