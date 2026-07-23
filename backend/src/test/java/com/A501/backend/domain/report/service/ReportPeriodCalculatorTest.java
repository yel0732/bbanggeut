package com.A501.backend.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.A501.backend.domain.report.model.ReportPeriod;

class ReportPeriodCalculatorTest {

    @Test
    void KST_기준_전날_19시부터_당일_19시까지_집계한다() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-23T10:00:00Z"),
                ZoneId.of("Asia/Seoul"));
        ReportPeriodCalculator calculator = new ReportPeriodCalculator(clock);

        ReportPeriod period = calculator.calculate();

        assertThat(period.startedAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 22, 19, 0));
        assertThat(period.endedAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 23, 19, 0));
        assertThat(period.reportDate())
                .isEqualTo(LocalDate.of(2026, 7, 23));
    }
}
