package com.A501.backend.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.child.entity.Child;
import com.A501.backend.domain.child.repository.ChildRepository;
import com.A501.backend.domain.report.entity.AdminReportGenerationAudit;
import com.A501.backend.domain.report.entity.DailyReport;
import com.A501.backend.domain.report.entity.ReportNotificationPreference;
import com.A501.backend.domain.report.exception.AdminReportErrorCode;
import com.A501.backend.domain.report.model.DailyReportCandidate;
import com.A501.backend.domain.report.model.ReportPeriod;
import com.A501.backend.domain.report.port.ConversationReportQueryPort;
import com.A501.backend.domain.report.port.GameReportQueryPort;
import com.A501.backend.domain.report.repository.AdminReportGenerationAuditRepository;
import com.A501.backend.domain.report.repository.DailyReportRepository;
import com.A501.backend.domain.report.repository.ReportNotificationPreferenceRepository;

class AdminHistoricalReportGenerationServiceTest {

    private final ChildRepository childRepository = mock(ChildRepository.class);
    private final ReportNotificationPreferenceRepository preferenceRepository =
            mock(ReportNotificationPreferenceRepository.class);
    private final DailyReportRepository dailyReportRepository =
            mock(DailyReportRepository.class);
    private final AdminReportGenerationAuditRepository auditRepository =
            mock(AdminReportGenerationAuditRepository.class);
    private final DailyReportRegistrationService registrationService =
            mock(DailyReportRegistrationService.class);
    private final GameReportQueryPort gamePort = mock(GameReportQueryPort.class);
    private final ConversationReportQueryPort conversationPort =
            mock(ConversationReportQueryPort.class);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-08-04T03:00:00Z"), ZoneOffset.UTC);

    private AdminHistoricalReportGenerationService service;

    @BeforeEach
    void setUp() {
        service = new AdminHistoricalReportGenerationService(
                childRepository,
                preferenceRepository,
                dailyReportRepository,
                auditRepository,
                registrationService,
                new ReportPeriodCalculator(clock),
                provider(gamePort),
                provider(conversationPort),
                clock);
    }

    @Test
    void generate_usesParentsDeliveryTimeAndTimezoneAndRecordsAudit() {
        LocalDate reportDate = LocalDate.of(2026, 8, 3);
        Child child = child(10L, 11L);
        ReportNotificationPreference preference =
                ReportNotificationPreference.defaults(11L);
        preference.update(true, LocalTime.of(16, 0), "Asia/Seoul");
        DailyReport report = DailyReport.pending(10L, new ReportPeriod(
                LocalDateTime.of(2026, 8, 2, 15, 55),
                LocalDateTime.of(2026, 8, 3, 15, 55),
                reportDate));
        ReflectionTestUtils.setField(report, "id", 20L);

        when(childRepository.findByChildIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(child));
        when(preferenceRepository.findByParentId(11L))
                .thenReturn(Optional.of(preference));
        when(gamePort.existsCompletedGame(
                10L,
                LocalDateTime.of(2026, 8, 2, 15, 55),
                LocalDateTime.of(2026, 8, 3, 15, 55)))
                .thenReturn(true);
        when(registrationService.register(any(DailyReportCandidate.class)))
                .thenReturn(true);
        when(dailyReportRepository.findByChildIdAndReportDate(10L, reportDate))
                .thenReturn(Optional.of(report));

        var response = service.generate(1L, 10L, reportDate);

        assertThat(response.periodStartedAt())
                .isEqualTo(LocalDateTime.of(2026, 8, 2, 15, 55));
        assertThat(response.periodEndedAt())
                .isEqualTo(LocalDateTime.of(2026, 8, 3, 15, 55));
        verify(auditRepository).save(any(AdminReportGenerationAudit.class));
    }

    @Test
    void generate_rejectsDateThatIsNotPastInParentsTimezone() {
        Child child = child(10L, 11L);
        when(childRepository.findByChildIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(child));
        when(preferenceRepository.findByParentId(11L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate(
                1L, 10L, LocalDate.of(2026, 8, 4)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(
                                AdminReportErrorCode.REPORT_DATE_NOT_PAST));
    }

    @Test
    void generate_rejectsPeriodWithoutReportableActivity() {
        Child child = child(10L, 11L);
        when(childRepository.findByChildIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(child));
        when(preferenceRepository.findByParentId(11L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate(
                1L, 10L, LocalDate.of(2026, 8, 3)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(
                                AdminReportErrorCode.SOURCE_NOT_FOUND));
    }

    @Test
    void generate_rejectsDuplicateReportForTheSameChildAndDate() {
        LocalDate reportDate = LocalDate.of(2026, 8, 3);
        Child child = child(10L, 11L);
        when(childRepository.findByChildIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(child));
        when(preferenceRepository.findByParentId(11L)).thenReturn(Optional.empty());
        when(gamePort.existsCompletedGame(any(), any(), any())).thenReturn(true);
        when(registrationService.register(any(DailyReportCandidate.class)))
                .thenReturn(false);

        assertThatThrownBy(() -> service.generate(1L, 10L, reportDate))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(
                                AdminReportErrorCode.REPORT_ALREADY_EXISTS));
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    private static Child child(Long childId, Long parentId) {
        Child child = Child.create(parentId, "child", "preset");
        ReflectionTestUtils.setField(child, "childId", childId);
        return child;
    }
}
