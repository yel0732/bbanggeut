package com.A501.backend.domain.game.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record SaveRecyclingResultRequest(
        @NotBlank @Size(max = 40) String rulesVersion,
        @NotNull @PositiveOrZero Integer successfulCount,
        @NotNull @PositiveOrZero Integer wrongCount
) {
}
