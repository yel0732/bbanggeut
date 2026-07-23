package com.A501.backend.domain.report.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.A501.backend.domain.report.batch.processor.DailyReportProcessor;
import com.A501.backend.domain.report.model.DailyReportCandidate;
import com.A501.backend.domain.report.model.ReportPeriod;
import com.A501.backend.domain.report.port.ConversationReportQueryPort;
import com.A501.backend.domain.report.port.GameReportQueryPort;

class DailyReportProcessorTest {

    private final ReportPeriod period = new ReportPeriod(
            LocalDateTime.of(2026, 7, 22, 19, 0),
            LocalDateTime.of(2026, 7, 23, 19, 0),
            LocalDate.of(2026, 7, 23));

    @Test
    void 게임과_대화가_모두_없으면_리포트_대상에서_제외한다() {
        DailyReportProcessor processor = createProcessor(false, false);

        DailyReportCandidate candidate = processor.process(1L);

        assertThat(candidate).isNull();
    }

    @Test
    void 게임만_있으면_리포트_대상이다() {
        DailyReportProcessor processor = createProcessor(true, false);

        DailyReportCandidate candidate = processor.process(2L);

        assertThat(candidate).isEqualTo(new DailyReportCandidate(2L, period));
    }

    @Test
    void 대화만_있으면_리포트_대상이다() {
        DailyReportProcessor processor = createProcessor(false, true);

        DailyReportCandidate candidate = processor.process(3L);

        assertThat(candidate).isEqualTo(new DailyReportCandidate(3L, period));
    }

    @Test
    void 게임과_대화가_모두_있어도_리포트_대상은_한_건이다() {
        DailyReportProcessor processor = createProcessor(true, true);

        DailyReportCandidate candidate = processor.process(4L);

        assertThat(candidate).isEqualTo(new DailyReportCandidate(4L, period));
    }

    private DailyReportProcessor createProcessor(
            boolean hasGame,
            boolean hasConversation) {
        GameReportQueryPort gamePort =
                (childId, startedAt, endedAt) -> hasGame;
        ConversationReportQueryPort conversationPort =
                (childId, startedAt, endedAt) -> hasConversation;

        return new DailyReportProcessor(
                gamePort,
                conversationPort,
                period);
    }
}
