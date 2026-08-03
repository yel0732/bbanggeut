package com.A501.backend.domain.report.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

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
        LocalDate reportDate = resolveDueReportDate(schedule, instant)
                .orElseGet(() -> instant.atZone(zone).toLocalDate());
        return calculate(schedule, reportDate);
    }

    private ReportPeriod calculate(
            ReportDeliverySchedule schedule,
            LocalDate reportDate) {
        LocalDateTime endedAt = reportDate
                .atTime(schedule.deliveryTime())
                .minus(GENERATION_LEAD_TIME);
        LocalDateTime startedAt = endedAt.minusDays(1);

        return new ReportPeriod(startedAt, endedAt, reportDate);
    }

    public boolean isGenerationDue(
            ReportDeliverySchedule schedule,
            Instant instant) {
        return resolveDueReportDate(schedule, instant).isPresent();
    }

    private Optional<LocalDate> resolveDueReportDate(
            ReportDeliverySchedule schedule,
            Instant instant) {
        ZoneId zone = ZoneId.of(schedule.timezone());
        ZonedDateTime now = instant.atZone(zone);
        LocalDate localDate = now.toLocalDate();

        // Midnight delivery times start generating on the previous date.
        // Prefer tomorrow's report once its lead time has started.
        LocalDate tomorrow = localDate.plusDays(1);
        if (!generationAt(schedule, tomorrow, zone).isAfter(now)) {
            return Optional.of(tomorrow);
        }
        if (!generationAt(schedule, localDate, zone).isAfter(now)) {
            return Optional.of(localDate);
        }
        return Optional.empty();
    }

    private ZonedDateTime generationAt(
            ReportDeliverySchedule schedule,
            LocalDate reportDate,
            ZoneId zone) {
        return reportDate.atTime(schedule.deliveryTime())
                .atZone(zone)
                .minus(GENERATION_LEAD_TIME);
    }
}
