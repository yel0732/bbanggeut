package com.A501.backend.domain.auth.dto.request;

// PIN은 선택값이다 — PIN 미설정 계정은 재인증 없이 탈퇴하므로 PIN을 보내지 않는다.
// PIN이 설정된 계정의 검증은 서비스 계층(PinService.verifyPin)이 담당한다.
public record WithdrawRequest(String pin) {
}
