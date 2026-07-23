package com.A501.backend.domain.report.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

import com.A501.backend.domain.report.model.ReportPeriod;

@Component
public class ReportPeriodCalculator {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime CUTOFF_TIME = LocalTime.of(19, 0);

    private final Clock clock;

    public ReportPeriodCalculator(Clock clock) {
        this.clock = clock;
    }

    public ReportPeriod calculate() {
        LocalDate reportDate = LocalDate.now(clock.withZone(KST));
        LocalDateTime endedAt = reportDate.atTime(CUTOFF_TIME);
        LocalDateTime startedAt = endedAt.minusDays(1);

        return new ReportPeriod(startedAt, endedAt, reportDate);
    }
}
