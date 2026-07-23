package com.A501.backend.domain.report.service;

import org.springframework.stereotype.Component;

import com.A501.backend.domain.report.model.ReportRetryProperties;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ReportRetryWaiter {

    private final ReportRetryProperties properties;

    public void waitForNextAttempt() {
        try {
            Thread.sleep(properties.interval().toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Report retry wait was interrupted.",
                    exception);
        }
    }
}
