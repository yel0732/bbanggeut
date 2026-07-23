package com.A501.backend.domain.auth.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;

public record SignupRequest(
        @Email @NotBlank @Pattern(regexp = EmailPatterns.WITH_TLD) String email,
        @ValidPassword String password,
        @NotNull @Past LocalDate birthDate
) {
}
