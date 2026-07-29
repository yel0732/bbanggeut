package com.A501.backend.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

// 클라이언트가 보낼 수 있는 것은 프리셋 키뿐이다. 업로드(웹캠) 사진의 키는 서버가 생성하므로
// 이 요청으로는 지정할 수 없다 — 타인 키를 자기 프로필에 붙이는 공격면을 없애기 위함이다.
public record UpdateProfileImageRequest(
        @NotBlank String imageKey
) {
}
