package com.A501.backend.domain.report.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.A501.backend.domain.report.repository.DailyReportRepository;
import com.A501.backend.domain.report.repository.ReportConversationSummaryRepository;
import com.A501.backend.domain.report.repository.ReportGameResultRepository;
import com.A501.backend.domain.report.repository.ReportGenerationAttemptRepository;
import com.A501.backend.domain.report.repository.ReportManualRegenerationRepository;
import com.A501.backend.domain.report.repository.ReportNotificationFailureRepository;
import com.A501.backend.domain.report.repository.ReportNotificationRepository;
import com.A501.backend.domain.report.repository.ReportNotificationPreferenceRepository;

@ExtendWith(MockitoExtension.class)
class ReportPurgeServiceTest {

    @Mock
    private DailyReportRepository dailyReportRepository;
    @Mock
    private ReportGameResultRepository reportGameResultRepository;
    @Mock
    private ReportConversationSummaryRepository reportConversationSummaryRepository;
    @Mock
    private ReportGenerationAttemptRepository reportGenerationAttemptRepository;
    @Mock
    private ReportNotificationFailureRepository reportNotificationFailureRepository;
    @Mock
    private ReportNotificationRepository reportNotificationRepository;
    @Mock
    private ReportNotificationPreferenceRepository reportNotificationPreferenceRepository;
    @Mock
    private ReportManualRegenerationRepository reportManualRegenerationRepository;

    @InjectMocks
    private ReportPurgeService reportPurgeService;

    @Test
    void skipsPurgeWhenChildIdsAreEmpty() {
        reportPurgeService.purgeByParentIdAndChildIds(10L, List.of());

        verify(reportNotificationPreferenceRepository).deleteByParentId(10L);
        verifyNoInteractions(
                dailyReportRepository,
                reportGameResultRepository,
                reportConversationSummaryRepository,
                reportGenerationAttemptRepository,
                reportNotificationFailureRepository,
                reportNotificationRepository,
                reportManualRegenerationRepository);
    }

    @Test
    void rejectsInvalidParentIdBeforeDeletingReports() {
        assertThatThrownBy(
                        () -> reportPurgeService.purgeByParentIdAndChildIds(
                                null,
                                List.of(1L)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(
                dailyReportRepository,
                reportGameResultRepository,
                reportConversationSummaryRepository,
                reportGenerationAttemptRepository,
                reportNotificationFailureRepository,
                reportNotificationRepository,
                reportNotificationPreferenceRepository,
                reportManualRegenerationRepository);
    }

    @Test
    void deletesParentScopedRowsWhenNoDailyReportsExist() {
        long parentId = 10L;
        List<Long> childIds = List.of(1L);
        when(dailyReportRepository.findIdsByChildIds(childIds)).thenReturn(List.of());

        reportPurgeService.purgeByParentIdAndChildIds(parentId, childIds);

        verify(reportNotificationRepository).deleteByParentId(parentId);
        verify(reportManualRegenerationRepository).deleteByParentId(parentId);
        verify(dailyReportRepository, never()).deleteByChildIds(childIds);
        verifyNoInteractions(
                reportGameResultRepository,
                reportConversationSummaryRepository,
                reportGenerationAttemptRepository,
                reportNotificationFailureRepository);
    }

    @Test
    void deletesReportRowsFromLeavesToDailyReport() {
        long parentId = 10L;
        List<Long> childIds = List.of(1L, 2L);
        List<Long> reportIds = List.of(100L, 101L);
        when(dailyReportRepository.findIdsByChildIds(childIds)).thenReturn(reportIds);

        reportPurgeService.purgeByParentIdAndChildIds(parentId, childIds);

        InOrder deletionOrder = inOrder(
                reportGameResultRepository,
                reportConversationSummaryRepository,
                reportGenerationAttemptRepository,
                reportNotificationFailureRepository,
                reportNotificationRepository,
                reportManualRegenerationRepository,
                dailyReportRepository);
        deletionOrder.verify(reportGameResultRepository).deleteByReportIds(reportIds);
        deletionOrder.verify(reportConversationSummaryRepository).deleteByReportIds(reportIds);
        deletionOrder.verify(reportGenerationAttemptRepository).deleteByReportIds(reportIds);
        deletionOrder.verify(reportNotificationFailureRepository).deleteByReportIds(reportIds);
        deletionOrder.verify(reportNotificationRepository)
                .deleteByReportIdsOrParentId(reportIds, parentId);
        deletionOrder.verify(reportManualRegenerationRepository)
                .deleteByReportIdsOrParentId(reportIds, parentId);
        deletionOrder.verify(dailyReportRepository).deleteByChildIds(childIds);
    }
}
