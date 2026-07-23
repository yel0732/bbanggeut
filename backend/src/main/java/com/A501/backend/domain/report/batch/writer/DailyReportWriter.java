package com.A501.backend.domain.report.batch.writer;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

import com.A501.backend.domain.report.model.DailyReportCandidate;
import com.A501.backend.domain.report.service.DailyReportRegistrationService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DailyReportWriter implements ItemWriter<DailyReportCandidate> {

    private final DailyReportRegistrationService registrationService;

    @Override
    public void write(Chunk<? extends DailyReportCandidate> chunk) {
        for (DailyReportCandidate candidate : chunk) {
            registrationService.register(candidate);
        }
    }
}
