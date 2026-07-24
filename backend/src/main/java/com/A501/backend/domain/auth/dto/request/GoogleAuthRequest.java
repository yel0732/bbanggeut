package com.A501.backend.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record GoogleAuthRequest(
        @NotBlank String idToken
) {
}
