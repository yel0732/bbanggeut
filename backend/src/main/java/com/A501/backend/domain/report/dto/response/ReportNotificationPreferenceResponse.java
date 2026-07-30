package com.A501.backend.domain.report.dto.response;

import java.time.LocalTime;

import com.A501.backend.domain.report.entity.ReportNotificationPreference;
import com.fasterxml.jackson.annotation.JsonFormat;

public record ReportNotificationPreferenceResponse(
        boolean enabled,
        @JsonFormat(pattern = "HH:mm") LocalTime time,
        String timezone
) {
    public static ReportNotificationPreferenceResponse from(
            ReportNotificationPreference preference
    ) {
        return new ReportNotificationPreferenceResponse(
                preference.isEnabled(),
                preference.getNotificationTime(),
                preference.getTimeZone());
    }
}
