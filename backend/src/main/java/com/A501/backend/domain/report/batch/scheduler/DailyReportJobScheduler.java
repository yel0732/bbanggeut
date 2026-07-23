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

    public DailyReportJobScheduler(
            JobLauncher jobLauncher,
            @Qualifier("dailyReportJob") Job dailyReportJob,
            ReportPeriodCalculator periodCalculator) {
        this.jobLauncher = jobLauncher;
        this.dailyReportJob = dailyReportJob;
        this.periodCalculator = periodCalculator;
    }

    @Scheduled(
            cron = "${report.schedule.cron}",
            zone = "${report.schedule.zone}")
    public void run() throws Exception {
        ReportPeriod period = periodCalculator.calculate();

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
                .toJobParameters();

        try {
            jobLauncher.run(dailyReportJob, parameters);
        } catch (JobExecutionAlreadyRunningException
                | JobInstanceAlreadyCompleteException exception) {
            log.info(
                    "Daily report job skipped because reportDate={} is already running or completed.",
                    period.reportDate());
        }
    }
}
