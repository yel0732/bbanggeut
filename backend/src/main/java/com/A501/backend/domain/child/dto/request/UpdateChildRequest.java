package com.A501.backend.domain.child.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 클라이언트가 지정할 수 있는 것은 프리셋 키뿐이다. 웹캠 사진은 별도 업로드 엔드포인트에서
// 서버가 키를 생성해 적용한다.
public record UpdateChildRequest(
        @NotBlank @Size(max = 15) String name,
        @NotBlank String imageKey
) {
}
