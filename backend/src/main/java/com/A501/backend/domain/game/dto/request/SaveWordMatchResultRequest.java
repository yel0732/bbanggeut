package com.A501.backend.domain.game.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record SaveWordMatchResultRequest(
        @NotBlank @Size(max = 40) String rulesVersion,
        @PositiveOrZero int solvedWordCount,
        @PositiveOrZero int incompleteWordCount,
        @PositiveOrZero int wrongCount,
        @PositiveOrZero int hintUsedCount,
        @NotEmpty List<@Valid SaveWordMatchProblemResultRequest> problemResults
) {
    public SaveWordMatchResultRequest {
        problemResults = problemResults == null ? null : List.copyOf(problemResults);
    }
}
