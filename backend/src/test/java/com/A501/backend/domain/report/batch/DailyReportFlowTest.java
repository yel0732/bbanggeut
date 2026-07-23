package com.A501.backend.domain.report.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.A501.backend.config.JpaAuditingConfig;
import com.A501.backend.domain.report.batch.processor.DailyReportProcessor;
import com.A501.backend.domain.report.entity.ReportGenerationStatus;
import com.A501.backend.domain.report.model.DailyReportCandidate;
import com.A501.backend.domain.report.model.ReportPeriod;
import com.A501.backend.domain.report.model.ReportGameSource;
import com.A501.backend.domain.report.port.ConversationReportQueryPort;
import com.A501.backend.domain.report.port.GameReportQueryPort;
import com.A501.backend.domain.report.repository.DailyReportRepository;
import com.A501.backend.domain.report.service.DailyReportRegistrationPersistenceService;
import com.A501.backend.domain.report.service.DailyReportRegistrationService;

@DataJpaTest
@Import({
        JpaAuditingConfig.class,
        DailyReportRegistrationPersistenceService.class,
        DailyReportRegistrationService.class
})
class DailyReportFlowTest {

    @Autowired
    private DailyReportRepository dailyReportRepository;

    @Autowired
    private DailyReportRegistrationService registrationService;

    @Test
    void 가상_아이별_데이터에_따라_대상만_PENDING으로_등록한다() {
        ReportPeriod period = new ReportPeriod(
                LocalDateTime.of(2026, 7, 22, 19, 0),
                LocalDateTime.of(2026, 7, 23, 19, 0),
                LocalDate.of(2026, 7, 23));

        GameReportQueryPort gamePort = new GameReportQueryPort() {
            @Override
            public boolean existsCompletedGame(
                    Long childId,
                    LocalDateTime startedAt,
                    LocalDateTime endedAt
            ) {
                return childId == 2L || childId == 4L;
            }

            @Override
            public List<ReportGameSource> findCompletedGames(
                    Long childId,
                    LocalDateTime startedAt,
                    LocalDateTime endedAt
            ) {
                return List.of();
            }
        };
        ConversationReportQueryPort conversationPort =
                (childId, startedAt, endedAt) -> childId == 3L || childId == 4L;
        DailyReportProcessor processor = new DailyReportProcessor(
                gamePort,
                conversationPort,
                period);

        for (Long childId : List.of(1L, 2L, 3L, 4L)) {
            DailyReportCandidate candidate = processor.process(childId);
            if (candidate != null) {
                registrationService.register(candidate);
            }
        }

        var reports = dailyReportRepository.findAll();

        assertThat(reports).hasSize(3);
        assertThat(reports)
                .extracting(report -> report.getChildId())
                .containsExactlyInAnyOrder(2L, 3L, 4L);
        assertThat(reports)
                .allMatch(report ->
                        report.getGenerationStatus() == ReportGenerationStatus.PENDING);
    }
}
