package com.A501.backend.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

import com.A501.backend.domain.report.entity.DailyReport;
import com.A501.backend.domain.report.entity.ReportGenerationStatus;
import com.A501.backend.domain.report.event.ReportGenerationRequestedEvent;
import com.A501.backend.domain.report.model.ReportRecoveryProperties;
import com.A501.backend.domain.report.repository.DailyReportRepository;

class ReportRecoveryServiceTest {

    @Test
    void resetsInterruptedProcessingAndRequestsPendingReports() {
        DailyReportRepository repository = mock(DailyReportRepository.class);
        ApplicationEventPublisher publisher =
                mock(ApplicationEventPublisher.class);
        DailyReport stale = mock(DailyReport.class);
        when(repository.findRecoverableProcessingForUpdate(
                any(), any(Pageable.class)))
                .thenReturn(List.of(stale));
        when(repository.findIdsByGenerationStatusOrderById(
                any(), any(Pageable.class)))
                .thenReturn(List.of(1L, 2L));
        ReportRecoveryService service = new ReportRecoveryService(
                repository,
                new ReportRecoveryProperties(true, Duration.ofMinutes(10), 100),
                publisher,
                Clock.fixed(Instant.parse("2026-07-23T10:00:00Z"), ZoneOffset.UTC));

        int requested = service.recoverAndRequest();

        assertThat(requested).isEqualTo(2);
        verify(stale).recoverInterruptedProcessing();
        verify(repository).findIdsByGenerationStatusOrderById(
                org.mockito.ArgumentMatchers.eq(ReportGenerationStatus.PENDING),
                any(Pageable.class));
        ArgumentCaptor<ReportGenerationRequestedEvent> events =
                ArgumentCaptor.forClass(ReportGenerationRequestedEvent.class);
        verify(publisher, org.mockito.Mockito.times(2))
                .publishEvent(events.capture());
        assertThat(events.getAllValues())
                .extracting(ReportGenerationRequestedEvent::reportId)
                .containsExactly(1L, 2L);
    }
}
