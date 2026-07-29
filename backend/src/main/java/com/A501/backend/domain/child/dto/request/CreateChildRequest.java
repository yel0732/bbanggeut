package com.A501.backend.domain.child.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// imageKey는 선택값이다 — 생략하면 기본 프리셋으로 생성된다. 생성 시점에는 childId가 없어 웹캠
// 사진의 키를 만들 수 없으므로, 이 요청으로 지정할 수 있는 것은 프리셋 키뿐이다.
public record CreateChildRequest(
        @NotBlank @Size(max = 15) String name,
        String imageKey
) {
}
