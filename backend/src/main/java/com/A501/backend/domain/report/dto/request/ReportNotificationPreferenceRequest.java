package com.A501.backend.domain.report.dto.request;

import java.time.LocalTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonFormat;

public record ReportNotificationPreferenceRequest(
        boolean enabled,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime time,
        @NotBlank @Size(max = 50) String timezone
) {
}
