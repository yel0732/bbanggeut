package com.A501.backend.domain.auth.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

public record GoogleCompleteRequest(
        @NotBlank String signupToken,
        @NotNull @Past LocalDate birthDate
) {
}
