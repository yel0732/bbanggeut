package com.A501.backend.domain.report.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.report.repository.DailyReportRepository;
import com.A501.backend.domain.report.repository.ReportConversationSummaryRepository;
import com.A501.backend.domain.report.repository.ReportGameResultRepository;
import com.A501.backend.domain.report.repository.ReportGenerationAttemptRepository;
import com.A501.backend.domain.report.repository.ReportManualRegenerationRepository;
import com.A501.backend.domain.report.repository.ReportNotificationFailureRepository;
import com.A501.backend.domain.report.repository.ReportNotificationRepository;
import com.A501.backend.domain.report.repository.ReportNotificationPreferenceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReportPurgeService {

    private final DailyReportRepository dailyReportRepository;
    private final ReportGameResultRepository reportGameResultRepository;
    private final ReportConversationSummaryRepository reportConversationSummaryRepository;
    private final ReportGenerationAttemptRepository reportGenerationAttemptRepository;
    private final ReportNotificationFailureRepository reportNotificationFailureRepository;
    private final ReportNotificationRepository reportNotificationRepository;
    private final ReportNotificationPreferenceRepository reportNotificationPreferenceRepository;
    private final ReportManualRegenerationRepository reportManualRegenerationRepository;

    @Transactional
    public void purgeByChildIds(List<Long> childIds) {
        if (childIds == null || childIds.isEmpty()) {
            return;
        }

        List<Long> reportIds = dailyReportRepository.findIdsByChildIds(childIds);
        if (!reportIds.isEmpty()) {
            reportGameResultRepository.deleteByReportIds(reportIds);
            reportConversationSummaryRepository.deleteByReportIds(reportIds);
            reportGenerationAttemptRepository.deleteByReportIds(reportIds);
            reportNotificationFailureRepository.deleteByReportIds(reportIds);
        }
        reportNotificationRepository.deleteByChildIds(childIds);
        reportManualRegenerationRepository.deleteByChildIds(childIds);
        dailyReportRepository.deleteByChildIds(childIds);
    }

    @Transactional
    public void purgeByParentIdAndChildIds(Long parentId, List<Long> childIds) {
        if (parentId == null || parentId < 1) {
            throw new IllegalArgumentException("Parent ID must be positive.");
        }
        reportNotificationPreferenceRepository.deleteByParentId(parentId);
        if (childIds == null || childIds.isEmpty()) {
            return;
        }

        List<Long> reportIds = dailyReportRepository.findIdsByChildIds(childIds);
        if (reportIds.isEmpty()) {
            reportNotificationRepository.deleteByParentId(parentId);
            reportManualRegenerationRepository.deleteByParentId(parentId);
            return;
        }

        reportGameResultRepository.deleteByReportIds(reportIds);
        reportConversationSummaryRepository.deleteByReportIds(reportIds);
        reportGenerationAttemptRepository.deleteByReportIds(reportIds);
        reportNotificationFailureRepository.deleteByReportIds(reportIds);
        reportNotificationRepository.deleteByReportIdsOrParentId(reportIds, parentId);
        reportManualRegenerationRepository.deleteByReportIdsOrParentId(reportIds, parentId);
        dailyReportRepository.deleteByChildIds(childIds);
    }
}
