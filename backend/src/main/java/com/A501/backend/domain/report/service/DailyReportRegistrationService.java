package com.A501.backend.domain.report.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.report.entity.DailyReport;
import com.A501.backend.domain.report.model.DailyReportCandidate;
import com.A501.backend.domain.report.repository.DailyReportRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DailyReportRegistrationService {

    private final DailyReportRepository dailyReportRepository;

    @Transactional
    public boolean register(DailyReportCandidate candidate) {
        boolean alreadyExists = dailyReportRepository.existsByChildIdAndReportDate(
                candidate.childId(),
                candidate.period().reportDate());

        if (alreadyExists) {
            return false;
        }

        DailyReport dailyReport = DailyReport.pending(
                candidate.childId(),
                candidate.period());
        dailyReportRepository.save(dailyReport);
        return true;
    }
}
