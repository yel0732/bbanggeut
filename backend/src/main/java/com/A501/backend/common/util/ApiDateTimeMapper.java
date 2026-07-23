package com.A501.backend.common.util;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class ApiDateTimeMapper {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private ApiDateTimeMapper() {
    }

    public static OffsetDateTime utcToKst(LocalDateTime utcDateTime) {
        if (utcDateTime == null) {
            return null;
        }
        return utcDateTime.atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(KST)
                .toOffsetDateTime();
    }
}
