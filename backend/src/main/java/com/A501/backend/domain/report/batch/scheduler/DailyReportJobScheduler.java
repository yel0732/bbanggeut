package com.A501.backend.domain.report.batch.scheduler;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.A501.backend.domain.report.model.ReportPeriod;
import com.A501.backend.domain.report.model.ReportDeliverySchedule;
import com.A501.backend.domain.report.port.ChildReportQueryPort;
import com.A501.backend.domain.report.service.ReportPeriodCalculator;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "report.schedule",
        name = "enabled",
        havingValue = "true")
public class DailyReportJobScheduler {

    private final JobLauncher jobLauncher;
    private final Job dailyReportJob;
    private final ReportPeriodCalculator periodCalculator;
    private final ChildReportQueryPort childReportQueryPort;
    private final java.time.Clock clock;

    public DailyReportJobScheduler(
            JobLauncher jobLauncher,
            @Qualifier("dailyReportJob") Job dailyReportJob,
            ReportPeriodCalculator periodCalculator,
            ChildReportQueryPort childReportQueryPort,
            @Qualifier("utcClock") java.time.Clock clock) {
        this.jobLauncher = jobLauncher;
        this.dailyReportJob = dailyReportJob;
        this.periodCalculator = periodCalculator;
        this.childReportQueryPort = childReportQueryPort;
        this.clock = clock;
    }

    @Scheduled(
            cron = "${report.schedule.cron}",
            zone = "${report.schedule.zone}")
    public void run() {
        java.time.Instant now = clock.instant();
        for (ReportDeliverySchedule schedule
                : childReportQueryPort.findActiveDeliverySchedules()) {
            if (periodCalculator.isGenerationDue(schedule, now)) {
                try {
                    launch(schedule, periodCalculator.calculate(schedule, now));
                } catch (Exception exception) {
                    // One failed schedule must not prevent other delivery times
                    // from being registered. The next scheduler tick retries it.
                    log.error(
                            "Daily report job launch failed. deliveryTime={}, timezone={}",
                            schedule.deliveryTime(),
                            schedule.timezone(),
                            exception);
                }
            }
        }
    }

    private void launch(
            ReportDeliverySchedule schedule,
            ReportPeriod period) throws Exception {

        JobParameters parameters = new JobParametersBuilder()
                .addString(
                        "reportDate",
                        period.reportDate().toString(),
                        true)
                .addString(
                        "startedAt",
                        period.startedAt().toString(),
                        false)
                .addString(
                        "endedAt",
                        period.endedAt().toString(),
                        false)
                .addString(
                        "deliveryTime",
                        schedule.deliveryTime().toString(),
                        true)
                .addString(
                        "timezone",
                        schedule.timezone(),
                        true)
                .toJobParameters();

        try {
            jobLauncher.run(dailyReportJob, parameters);
        } catch (JobExecutionAlreadyRunningException
                | JobInstanceAlreadyCompleteException exception) {
            log.debug(
                    "Daily report job skipped because reportDate={}, deliveryTime={}, timezone={} is already running or completed.",
                    period.reportDate(),
                    schedule.deliveryTime(),
                    schedule.timezone());
        }
    }
}
