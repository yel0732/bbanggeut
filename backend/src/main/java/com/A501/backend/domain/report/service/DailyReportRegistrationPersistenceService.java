package com.A501.backend.domain.report.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.report.entity.DailyReport;
import com.A501.backend.domain.report.model.DailyReportCandidate;
import com.A501.backend.domain.report.repository.DailyReportRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DailyReportRegistrationPersistenceService {

    private final DailyReportRepository dailyReportRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void register(DailyReportCandidate candidate) {
        DailyReport dailyReport = DailyReport.pending(
                candidate.childId(),
                candidate.period());
        dailyReportRepository.saveAndFlush(dailyReport);
    }
}
