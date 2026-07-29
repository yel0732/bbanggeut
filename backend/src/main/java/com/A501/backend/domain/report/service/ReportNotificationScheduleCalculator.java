package com.A501.backend.domain.report.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.A501.backend.domain.report.entity.ReportNotificationPreference;

@Component
public class ReportNotificationScheduleCalculator {

    private final Clock clock;

    public ReportNotificationScheduleCalculator(
            @Qualifier("utcClock") Clock clock
    ) {
        this.clock = clock;
    }

    public DeliveryPlan calculate(
            Optional<ReportNotificationPreference> preference
    ) {
        return preference.map(this::calculate)
                .orElseGet(() -> new DeliveryPlan(now(), true));
    }

    public DeliveryPlan calculate(ReportNotificationPreference preference) {
        if (!preference.isEnabled()) {
            return new DeliveryPlan(null, false);
        }

        Instant currentInstant = clock.instant();
        ZoneId zone = ZoneId.of(preference.getTimeZone());
        ZonedDateTime localNow = currentInstant.atZone(zone);
        Instant requested = localNow.toLocalDate()
                .atTime(preference.getNotificationTime())
                .atZone(zone)
                .toInstant();
        boolean deliverNow = !requested.isAfter(currentInstant);
        Instant scheduled = deliverNow ? currentInstant : requested;
        return new DeliveryPlan(
                LocalDateTime.ofInstant(scheduled, ZoneOffset.UTC),
                deliverNow);
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public record DeliveryPlan(LocalDateTime scheduledAt, boolean deliverNow) {
    }
}
