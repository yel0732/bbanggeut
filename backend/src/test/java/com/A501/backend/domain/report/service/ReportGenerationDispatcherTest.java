package com.A501.backend.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class ReportGenerationDispatcherTest {

    @Test
    void dispatchesGenerationAndRecordsCompletion() {
        DailyReportGenerationService generationService =
                mock(DailyReportGenerationService.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReportGenerationDispatcher dispatcher = new ReportGenerationDispatcher(
                generationService,
                Runnable::run,
                registry);

        assertThat(dispatcher.dispatch(1L)).isTrue();

        verify(generationService).generate(1L);
        assertThat(registry.counter("report.generation.dispatched").count())
                .isEqualTo(1);
        assertThat(registry.counter("report.generation.completed").count())
                .isEqualTo(1);
    }

    @Test
    void generationFailureDoesNotLeakInFlightRegistration() {
        DailyReportGenerationService generationService =
                mock(DailyReportGenerationService.class);
        doThrow(new IllegalStateException("failed"))
                .when(generationService).generate(2L);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReportGenerationDispatcher dispatcher = new ReportGenerationDispatcher(
                generationService,
                Runnable::run,
                registry);

        assertThat(dispatcher.dispatch(2L)).isTrue();
        assertThat(dispatcher.dispatch(2L)).isTrue();
        assertThat(registry.counter("report.generation.failed").count())
                .isEqualTo(2);
    }
}
