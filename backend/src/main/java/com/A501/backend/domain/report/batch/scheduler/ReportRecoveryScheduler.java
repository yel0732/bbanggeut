package com.A501.backend.domain.report.batch.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.A501.backend.domain.report.service.ReportRecoveryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "report.recovery",
        name = "enabled",
        havingValue = "true")
public class ReportRecoveryScheduler {

    private final ReportRecoveryService recoveryService;

    @Scheduled(
            fixedDelayString = "${report.recovery.interval:PT1M}",
            initialDelayString = "${report.recovery.initial-delay:PT30S}")
    public void recover() {
        int requested = recoveryService.recoverAndRequest();
        if (requested > 0) {
            log.info("Recovered pending report generation tasks. count={}", requested);
        }
    }
}
