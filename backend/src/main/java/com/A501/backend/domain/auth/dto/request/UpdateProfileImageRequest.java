package com.A501.backend.domain.auth.dto.request;

import jakarta.validation.constraints.Positive;

public record UpdateProfileImageRequest(
        // 허용 상한은 프론트 에셋 구성에 따라 추후 확정 — 지금은 양수만 검증한다.
        @Positive int profileImageNum
) {
}
