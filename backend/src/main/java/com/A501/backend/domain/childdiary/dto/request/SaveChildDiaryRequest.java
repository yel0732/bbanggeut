package com.A501.backend.domain.childdiary.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SaveChildDiaryRequest(@NotBlank String previewToken) {}
