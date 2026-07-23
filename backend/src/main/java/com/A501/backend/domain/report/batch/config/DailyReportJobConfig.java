package com.A501.backend.domain.report.batch.config;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.A501.backend.domain.report.batch.processor.DailyReportProcessor;
import com.A501.backend.domain.report.batch.reader.ActiveChildItemReader;
import com.A501.backend.domain.report.model.DailyReportCandidate;
import com.A501.backend.domain.report.model.ReportPeriod;
import com.A501.backend.domain.report.port.ChildReportQueryPort;
import com.A501.backend.domain.report.port.ConversationReportQueryPort;
import com.A501.backend.domain.report.port.GameReportQueryPort;

@Configuration
@ConditionalOnProperty(
        prefix = "report.schedule",
        name = "enabled",
        havingValue = "true")
public class DailyReportJobConfig {

    private static final int CHUNK_SIZE = 100;

    @Bean
    public Job dailyReportJob(
            JobRepository jobRepository,
            Step dailyReportStep) {
        return new JobBuilder("dailyReportJob", jobRepository)
                .start(dailyReportStep)
                .build();
    }

    @Bean
    public Step dailyReportStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ItemReader<Long> activeChildItemReader,
            ItemProcessor<Long, DailyReportCandidate> dailyReportProcessor,
            ItemWriter<DailyReportCandidate> dailyReportWriter) {
        return new StepBuilder("dailyReportStep", jobRepository)
                .<Long, DailyReportCandidate>chunk(CHUNK_SIZE, transactionManager)
                .reader(activeChildItemReader)
                .processor(dailyReportProcessor)
                .writer(dailyReportWriter)
                .build();
    }

    @Bean
    @StepScope
    public ItemReader<Long> activeChildItemReader(
            ChildReportQueryPort childReportQueryPort) {
        return new ActiveChildItemReader(childReportQueryPort);
    }

    @Bean
    @StepScope
    public ItemProcessor<Long, DailyReportCandidate> dailyReportProcessor(
            GameReportQueryPort gameReportQueryPort,
            ConversationReportQueryPort conversationReportQueryPort,
            @Value("#{jobParameters['startedAt']}") String startedAt,
            @Value("#{jobParameters['endedAt']}") String endedAt,
            @Value("#{jobParameters['reportDate']}") String reportDate) {
        ReportPeriod period = new ReportPeriod(
                LocalDateTime.parse(startedAt),
                LocalDateTime.parse(endedAt),
                LocalDate.parse(reportDate));

        return new DailyReportProcessor(
                gameReportQueryPort,
                conversationReportQueryPort,
                period);
    }
}
