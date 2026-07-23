package com.A501.backend.domain.report.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import com.A501.backend.domain.report.model.DailyReportCandidate;
import com.A501.backend.domain.report.repository.DailyReportRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DailyReportRegistrationService {

    private final DailyReportRepository dailyReportRepository;
    private final DailyReportRegistrationPersistenceService persistenceService;

    @Transactional
    public boolean register(DailyReportCandidate candidate) {
        boolean alreadyExists = dailyReportRepository.existsByChildIdAndReportDate(
                candidate.childId(),
                candidate.period().reportDate());

        if (alreadyExists) {
            return false;
        }

        try {
            persistenceService.register(candidate);
            return true;
        } catch (DataIntegrityViolationException exception) {
            boolean duplicate = dailyReportRepository.existsByChildIdAndReportDate(
                    candidate.childId(),
                    candidate.period().reportDate());
            if (duplicate) {
                return false;
            }
            throw exception;
        }
    }
}
