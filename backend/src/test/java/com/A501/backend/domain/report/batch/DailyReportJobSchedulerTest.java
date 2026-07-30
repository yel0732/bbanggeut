package com.A501.backend.domain.report.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobLauncher;

import com.A501.backend.domain.report.batch.scheduler.DailyReportJobScheduler;
import com.A501.backend.domain.report.model.ReportPeriod;
import com.A501.backend.domain.report.model.ReportDeliverySchedule;
import com.A501.backend.domain.report.port.ChildReportQueryPort;
import com.A501.backend.domain.report.service.ReportPeriodCalculator;

@ExtendWith(MockitoExtension.class)
class DailyReportJobSchedulerTest {

    @Mock
    private JobLauncher jobLauncher;

    @Mock
    private Job dailyReportJob;

    @Mock
    private ReportPeriodCalculator periodCalculator;

    @Mock
    private ChildReportQueryPort childReportQueryPort;

    private final Clock clock = Clock.fixed(
            Instant.parse("2026-07-23T10:55:00Z"), ZoneOffset.UTC);
    private final ReportDeliverySchedule schedule =
            new ReportDeliverySchedule(java.time.LocalTime.of(20, 0), "Asia/Seoul");

    private DailyReportJobScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new DailyReportJobScheduler(
                jobLauncher,
                dailyReportJob,
                periodCalculator,
                childReportQueryPort,
                clock);
        when(childReportQueryPort.findActiveDeliverySchedules())
                .thenReturn(List.of(schedule));
        when(periodCalculator.isGenerationDue(schedule, clock.instant()))
                .thenReturn(true);
    }

    @Test
    void 동일_날짜의_실행_중이거나_완료된_Job은_정상적으로_건너뛴다()
            throws Exception {
        when(periodCalculator.calculate(schedule, clock.instant()))
                .thenReturn(period(LocalDate.of(2026, 7, 23)));
        when(jobLauncher.run(any(Job.class), any(JobParameters.class)))
                .thenThrow(new JobExecutionAlreadyRunningException("already running"))
                .thenThrow(new JobInstanceAlreadyCompleteException("already complete"));

        assertThatCode(scheduler::run).doesNotThrowAnyException();
        assertThatCode(scheduler::run).doesNotThrowAnyException();

        verify(jobLauncher, times(2))
                .run(any(Job.class), any(JobParameters.class));
    }

    @Test
    void reportDate만_JobInstance_식별_파라미터로_사용한다() throws Exception {
        when(periodCalculator.calculate(schedule, clock.instant()))
                .thenReturn(period(LocalDate.of(2026, 7, 23)));

        scheduler.run();

        ArgumentCaptor<JobParameters> captor =
                ArgumentCaptor.forClass(JobParameters.class);
        verify(jobLauncher).run(any(Job.class), captor.capture());
        JobParameters parameters = captor.getValue();

        assertThat(parameters.getString("reportDate")).isEqualTo("2026-07-23");
        assertThat(parameters.getString("startedAt")).isEqualTo("2026-07-22T19:55");
        assertThat(parameters.getString("endedAt")).isEqualTo("2026-07-23T19:55");
        assertThat(parameters.getString("deliveryTime")).isEqualTo("20:00");
        assertThat(parameters.getString("timezone")).isEqualTo("Asia/Seoul");
        assertThat(parameters.getIdentifyingParameters()).hasSize(3);
        assertThat(parameters.getParameter("reportDate").identifying()).isTrue();
        assertThat(parameters.getParameter("startedAt").identifying()).isFalse();
        assertThat(parameters.getParameter("endedAt").identifying()).isFalse();
    }

    @Test
    void 서로_다른_날짜는_각각_별도_Job으로_실행한다() throws Exception {
        when(periodCalculator.calculate(schedule, clock.instant()))
                .thenReturn(period(LocalDate.of(2026, 7, 23)))
                .thenReturn(period(LocalDate.of(2026, 7, 24)));

        scheduler.run();
        scheduler.run();

        ArgumentCaptor<JobParameters> captor =
                ArgumentCaptor.forClass(JobParameters.class);
        verify(jobLauncher, times(2)).run(any(Job.class), captor.capture());

        assertThat(captor.getAllValues())
                .extracting(parameters -> parameters.getString("reportDate"))
                .containsExactly("2026-07-23", "2026-07-24");
    }

    private ReportPeriod period(LocalDate reportDate) {
        LocalDateTime endedAt = reportDate.atTime(19, 55);
        return new ReportPeriod(
                endedAt.minusDays(1),
                endedAt,
                reportDate);
    }
}
