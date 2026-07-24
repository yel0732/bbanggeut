package com.A501.backend.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePinRequest(
        @NotBlank String currentPin,
        @NotBlank @Pattern(regexp = "\\d{4}", message = "PIN은 4자리 숫자여야 합니다.") String newPin,
        @NotBlank String newPinConfirm
) {
}
