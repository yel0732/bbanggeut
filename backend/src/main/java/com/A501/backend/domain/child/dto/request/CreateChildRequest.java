package com.A501.backend.domain.child.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateChildRequest(
        @NotBlank @Size(max = 15) String name,
        // 허용 상한은 프론트 에셋 구성에 따라 추후 확정 — 지금은 양수만 검증한다.
        @Positive int profileImageNum
) {
}
