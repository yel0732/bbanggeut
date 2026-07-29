package com.A501.backend.common.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    // 에러 코드만으로는 클라이언트가 화면을 구성할 수 없는 경우가 있어 부가 정보를 함께 싣는다.
    // 잠금 잔여시간과 남은 시도 횟수는 서버만 아는 값이다 — 실패 카운트가 계정 단위로 누적되므로
    // 클라이언트가 독립적으로 추적할 수 없고, 다른 기기에서 이미 실패한 이력까지 반영해야 한다.
    // 해당 없는 에러에서는 null이며 응답 본문에도 나가지 않는다.
    private final Integer retryAfterSeconds;
    private final Integer remainingAttempts;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null, null);
    }

    private BusinessException(ErrorCode errorCode, Integer retryAfterSeconds, Integer remainingAttempts) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.retryAfterSeconds = retryAfterSeconds;
        this.remainingAttempts = remainingAttempts;
    }

    // 잠금이 풀리기까지 남은 초. 절대 시각 대신 상대 초를 쓰는 이유는 두 가지다.
    // 1) pinLockedUntil은 오프셋 없는 KST 벽시계 LocalDateTime이라 그대로 직렬화하면
    //    클라이언트가 자기 타임존으로 해석해 어긋난다.
    // 2) 사용자 기기 시계가 틀려도 카운트다운이 정확하다.
    public static BusinessException retryAfter(ErrorCode errorCode, int retryAfterSeconds) {
        return new BusinessException(errorCode, retryAfterSeconds, null);
    }

    public static BusinessException withRemainingAttempts(ErrorCode errorCode, int remainingAttempts) {
        return new BusinessException(errorCode, null, remainingAttempts);
    }
}
