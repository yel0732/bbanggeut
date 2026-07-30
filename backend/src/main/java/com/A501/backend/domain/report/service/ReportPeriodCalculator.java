package com.A501.backend.domain.report.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Instant;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.A501.backend.domain.report.model.ReportPeriod;
import com.A501.backend.domain.report.model.ReportDeliverySchedule;

@Component
public class ReportPeriodCalculator {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final java.time.Duration GENERATION_LEAD_TIME =
            java.time.Duration.ofMinutes(5);

    private final Clock clock;

    public ReportPeriodCalculator(
            @Qualifier("reportClock") Clock clock
    ) {
        this.clock = clock;
    }

    public ReportPeriod calculate() {
        return calculate(
                new ReportDeliverySchedule(LocalTime.of(20, 0), KST.getId()),
                clock.instant());
    }

    public ReportPeriod calculate(
            ReportDeliverySchedule schedule,
            Instant instant) {
        ZoneId zone = ZoneId.of(schedule.timezone());
        LocalDate reportDate = instant.atZone(zone).toLocalDate();
        LocalDateTime endedAt = reportDate
                .atTime(schedule.deliveryTime())
                .minus(GENERATION_LEAD_TIME);
        LocalDateTime startedAt = endedAt.minusDays(1);

        return new ReportPeriod(startedAt, endedAt, reportDate);
    }

    public boolean isGenerationDue(
            ReportDeliverySchedule schedule,
            Instant instant) {
        ZoneId zone = ZoneId.of(schedule.timezone());
        LocalTime localMinute = instant.atZone(zone)
                .toLocalTime()
                .withSecond(0)
                .withNano(0);
        LocalTime generationTime = schedule.deliveryTime()
                .minus(GENERATION_LEAD_TIME);
        return localMinute.equals(generationTime);
    }
}
