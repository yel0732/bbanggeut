package com.A501.backend.domain.report.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ReportGenerationDispatcher {

    private final DailyReportGenerationService generationService;
    private final Executor executor;
    private final Set<Long> inFlightReportIds = ConcurrentHashMap.newKeySet();
    private final Counter dispatchedCounter;
    private final Counter duplicateCounter;
    private final Counter completedCounter;
    private final Counter failedCounter;

    public ReportGenerationDispatcher(
            DailyReportGenerationService generationService,
            @Qualifier("reportGenerationExecutor") Executor executor,
            MeterRegistry meterRegistry
    ) {
        this.generationService = generationService;
        this.executor = executor;
        this.dispatchedCounter = meterRegistry.counter(
                "report.generation.dispatched");
        this.duplicateCounter = meterRegistry.counter(
                "report.generation.duplicate");
        this.completedCounter = meterRegistry.counter(
                "report.generation.completed");
        this.failedCounter = meterRegistry.counter(
                "report.generation.failed");
    }

    public boolean dispatch(Long reportId) {
        if (reportId == null || reportId < 1) {
            throw new IllegalArgumentException("Report ID must be positive.");
        }
        if (!inFlightReportIds.add(reportId)) {
            duplicateCounter.increment();
            return false;
        }

        try {
            executor.execute(() -> generate(reportId));
            dispatchedCounter.increment();
            return true;
        } catch (RuntimeException exception) {
            inFlightReportIds.remove(reportId);
            throw exception;
        }
    }

    private void generate(Long reportId) {
        try {
            generationService.generate(reportId);
            completedCounter.increment();
        } catch (RuntimeException exception) {
            failedCounter.increment();
            log.error("Daily report generation failed. reportId={}", reportId, exception);
        } finally {
            inFlightReportIds.remove(reportId);
        }
    }
}
