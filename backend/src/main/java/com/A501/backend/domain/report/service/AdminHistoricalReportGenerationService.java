package com.A501.backend.domain.report.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.child.entity.Child;
import com.A501.backend.domain.child.repository.ChildRepository;
import com.A501.backend.domain.report.dto.response.ReportGenerationResponse;
import com.A501.backend.domain.report.entity.AdminReportGenerationAudit;
import com.A501.backend.domain.report.entity.DailyReport;
import com.A501.backend.domain.report.entity.ReportNotificationPreference;
import com.A501.backend.domain.report.exception.AdminReportErrorCode;
import com.A501.backend.domain.report.model.DailyReportCandidate;
import com.A501.backend.domain.report.model.ReportDeliverySchedule;
import com.A501.backend.domain.report.model.ReportPeriod;
import com.A501.backend.domain.report.port.ConversationReportQueryPort;
import com.A501.backend.domain.report.port.GameReportQueryPort;
import com.A501.backend.domain.report.repository.AdminReportGenerationAuditRepository;
import com.A501.backend.domain.report.repository.DailyReportRepository;
import com.A501.backend.domain.report.repository.ReportNotificationPreferenceRepository;

@Service
@Transactional(readOnly = true)
public class AdminHistoricalReportGenerationService {

    private final ChildRepository childRepository;
    private final ReportNotificationPreferenceRepository preferenceRepository;
    private final DailyReportRepository dailyReportRepository;
    private final AdminReportGenerationAuditRepository auditRepository;
    private final DailyReportRegistrationService registrationService;
    private final ReportPeriodCalculator periodCalculator;
    private final ObjectProvider<GameReportQueryPort> gameReportQueryPortProvider;
    private final ObjectProvider<ConversationReportQueryPort>
            conversationReportQueryPortProvider;
    private final Clock clock;

    public AdminHistoricalReportGenerationService(
            ChildRepository childRepository,
            ReportNotificationPreferenceRepository preferenceRepository,
            DailyReportRepository dailyReportRepository,
            AdminReportGenerationAuditRepository auditRepository,
            DailyReportRegistrationService registrationService,
            ReportPeriodCalculator periodCalculator,
            ObjectProvider<GameReportQueryPort> gameReportQueryPort,
            ObjectProvider<ConversationReportQueryPort> conversationReportQueryPort,
            @Qualifier("reportClock") Clock clock) {
        this.childRepository = childRepository;
        this.preferenceRepository = preferenceRepository;
        this.dailyReportRepository = dailyReportRepository;
        this.auditRepository = auditRepository;
        this.registrationService = registrationService;
        this.periodCalculator = periodCalculator;
        this.gameReportQueryPortProvider = gameReportQueryPort;
        this.conversationReportQueryPortProvider = conversationReportQueryPort;
        this.clock = clock;
    }

    @Transactional
    public ReportGenerationResponse generate(
            Long adminParentId,
            Long childId,
            LocalDate reportDate) {
        Child child = childRepository.findByChildIdAndDeletedAtIsNull(childId)
                .orElseThrow(() -> new BusinessException(
                        AdminReportErrorCode.CHILD_NOT_FOUND));
        ReportNotificationPreference preference = preferenceRepository
                .findByParentId(child.getParentId())
                .orElseGet(() -> ReportNotificationPreference.defaults(
                        child.getParentId()));
        ReportDeliverySchedule schedule = new ReportDeliverySchedule(
                preference.getNotificationTime(),
                preference.getTimeZone());

        LocalDate today = clock.instant()
                .atZone(ZoneId.of(schedule.timezone()))
                .toLocalDate();
        if (!reportDate.isBefore(today)) {
            throw new BusinessException(AdminReportErrorCode.REPORT_DATE_NOT_PAST);
        }

        ReportPeriod period = periodCalculator.calculate(schedule, reportDate);
        if (!hasActivity(childId, period)) {
            throw new BusinessException(AdminReportErrorCode.SOURCE_NOT_FOUND);
        }

        boolean registered = registrationService.register(
                new DailyReportCandidate(childId, period));
        if (!registered) {
            throw new BusinessException(AdminReportErrorCode.REPORT_ALREADY_EXISTS);
        }

        DailyReport report = dailyReportRepository
                .findByChildIdAndReportDate(childId, reportDate)
                .orElseThrow(() -> new IllegalStateException(
                        "Registered daily report was not found."));
        auditRepository.save(AdminReportGenerationAudit.create(
                adminParentId, childId, report.getId(), reportDate));
        return new ReportGenerationResponse(
                report.getId(),
                report.getChildId(),
                report.getReportDate(),
                report.getPeriodStartedAt(),
                report.getPeriodEndedAt(),
                report.getGenerationStatus());
    }

    private boolean hasActivity(Long childId, ReportPeriod period) {
        GameReportQueryPort gameReportQueryPort = required(
                gameReportQueryPortProvider);
        ConversationReportQueryPort conversationReportQueryPort = required(
                conversationReportQueryPortProvider);
        return gameReportQueryPort.existsCompletedGame(
                childId, period.startedAt(), period.endedAt())
                || conversationReportQueryPort.existsReportableConversation(
                        childId, period.startedAt(), period.endedAt());
    }

    private static <T> T required(ObjectProvider<T> provider) {
        T value = provider.getIfAvailable();
        if (value == null) {
            throw new IllegalStateException("Required report port is not connected.");
        }
        return value;
    }
}
