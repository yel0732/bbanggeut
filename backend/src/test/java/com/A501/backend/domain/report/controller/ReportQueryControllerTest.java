package com.A501.backend.domain.report.controller;

import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.report.entity.DailyReport;
import com.A501.backend.domain.report.entity.ReportConversationSummary;
import com.A501.backend.domain.report.entity.ReportGameResult;
import com.A501.backend.domain.report.exception.ReportApiErrorCode;
import com.A501.backend.domain.report.model.ReportConversationSummarySource;
import com.A501.backend.domain.report.model.ReportGameSource;
import com.A501.backend.domain.report.model.ReportPeriod;
import com.A501.backend.domain.report.port.ReportChildAccessVerifier;
import com.A501.backend.domain.report.port.ReportCurrentParentProvider;
import com.A501.backend.domain.report.port.ReportParentAccessTokenVerifier;
import com.A501.backend.domain.report.repository.DailyReportRepository;
import com.A501.backend.domain.report.repository.ReportConversationSummaryRepository;
import com.A501.backend.domain.report.repository.ReportGameResultRepository;

@SpringBootTest(properties = "report.schedule.enabled=false")
@AutoConfigureMockMvc
class ReportQueryControllerTest {

    private static final Long PARENT_ID = 1L;
    private static final Long CHILD_ID = 15L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DailyReportRepository dailyReportRepository;

    @Autowired
    private ReportGameResultRepository gameResultRepository;

    @Autowired
    private ReportConversationSummaryRepository summaryRepository;

    @MockitoBean
    private ReportCurrentParentProvider currentParentProvider;

    @MockitoBean
    private ReportChildAccessVerifier childAccessVerifier;

    @MockitoBean
    private ReportParentAccessTokenVerifier parentAccessTokenVerifier;

    @BeforeEach
    void setUp() {
        summaryRepository.deleteAll();
        gameResultRepository.deleteAll();
        dailyReportRepository.deleteAll();
        org.mockito.Mockito.when(currentParentProvider.getCurrentParentId())
                .thenReturn(PARENT_ID);
    }

    @Test
    void reportsCanBeFilteredByDateAndStatusWithPagination()
            throws Exception {
        createCompletedReport(LocalDate.of(2026, 7, 21));
        createFailedReport(LocalDate.of(2026, 7, 22));
        createCompletedReport(LocalDate.of(2026, 7, 23));

        mockMvc.perform(get("/api/children/{childId}/reports", CHILD_ID)
                        .header("X-Parent-Access-Token", "parent-token")
                        .queryParam("startDate", "2026-07-22")
                        .queryParam("endDate", "2026-07-23")
                        .queryParam("status", "COMPLETED")
                        .queryParam("page", "0")
                        .queryParam("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reports.length()").value(1))
                .andExpect(jsonPath("$.reports[0].reportDate")
                        .value("2026-07-23"))
                .andExpect(jsonPath("$.reports[0].status")
                        .value("COMPLETED"))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andExpect(jsonPath("$.page.hasNext").value(false));
    }

    @Test
    void completedReportIncludesGameAndConversationContent()
            throws Exception {
        DailyReport report = createCompletedReport(
                LocalDate.of(2026, 7, 23));
        gameResultRepository.save(ReportGameResult.snapshot(
                report.getId(),
                new ReportGameSource(
                        UUID.randomUUID(),
                        3L,
                        "DOBBLE",
                        "도블",
                        LocalDateTime.of(2026, 7, 23, 18, 0),
                        120,
                        85,
                        null),
                "{\"solvedCardCount\":7}"));
        summaryRepository.save(ReportConversationSummary.snapshot(
                report.getId(),
                new ReportConversationSummarySource(
                        List.of("conversation-1"),
                        "즐거운 하루",
                        "친구와 재미있게 놀았어요.",
                        List.of("친구와 놀이"),
                        List.of("기쁨"),
                        List.of("카드 게임"),
                        "model-v1",
                        "prompt-v1"),
                "[\"conversation-1\"]",
                "[\"친구와 놀이\"]",
                "[\"기쁨\"]",
                "[\"카드 게임\"]"));

        mockMvc.perform(get(
                        "/api/children/{childId}/reports/{reportDate}",
                        CHILD_ID,
                        "2026-07-23")
                        .header("X-Parent-Access-Token", "parent-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.gameResults.length()").value(1))
                .andExpect(jsonPath("$.gameResults[0].gameType").value("DOBBLE"))
                .andExpect(jsonPath(
                        "$.gameResults[0].resultMetrics.solvedCardCount")
                        .value(7))
                .andExpect(jsonPath("$.conversationSummary.diaryTitle")
                        .value("즐거운 하루"))
                .andExpect(jsonPath(
                        "$.conversationSummary.expressedEmotions[0]")
                        .value("기쁨"))
                .andExpect(jsonPath("$.failure").doesNotExist());
    }

    @Test
    void failedReportOnlyIncludesFailureInformation() throws Exception {
        createFailedReport(LocalDate.of(2026, 7, 23));

        mockMvc.perform(get(
                        "/api/children/{childId}/reports/{reportDate}",
                        CHILD_ID,
                        "2026-07-23")
                        .header("X-Parent-Access-Token", "parent-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failure.message")
                        .value("summary generation failed"))
                .andExpect(jsonPath("$.failure.canRegenerate").value(true))
                .andExpect(jsonPath("$.gameResults.length()").value(0))
                .andExpect(jsonPath("$.conversationSummary").doesNotExist());
    }

    @Test
    void reportNotFoundReturnsNotFound() throws Exception {
        mockMvc.perform(get(
                        "/api/children/{childId}/reports/{reportDate}",
                        CHILD_ID,
                        "2026-07-23")
                        .header("X-Parent-Access-Token", "parent-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("REPORT_NOT_FOUND"));
    }

    @Test
    void childOwnershipFailureIsHiddenAsNotFound() throws Exception {
        doThrow(new BusinessException(ReportApiErrorCode.CHILD_NOT_FOUND))
                .when(childAccessVerifier)
                .verifyOwnership(PARENT_ID, CHILD_ID);

        mockMvc.perform(get("/api/children/{childId}/reports", CHILD_ID)
                        .header("X-Parent-Access-Token", "parent-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CHILD_NOT_FOUND"));
    }

    @Test
    void invalidDateRangeIsRejected() throws Exception {
        mockMvc.perform(get("/api/children/{childId}/reports", CHILD_ID)
                        .header("X-Parent-Access-Token", "parent-token")
                        .queryParam("startDate", "2026-07-24")
                        .queryParam("endDate", "2026-07-23"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    private DailyReport createCompletedReport(LocalDate reportDate) {
        DailyReport report = DailyReport.pending(
                CHILD_ID,
                period(reportDate));
        report.startProcessing(reportDate.atTime(19, 1));
        report.complete(reportDate.atTime(19, 2));
        return dailyReportRepository.saveAndFlush(report);
    }

    private DailyReport createFailedReport(LocalDate reportDate) {
        DailyReport report = DailyReport.pending(
                CHILD_ID,
                period(reportDate));
        report.startProcessing(reportDate.atTime(19, 1));
        report.fail(
                reportDate.atTime(19, 2),
                "summary generation failed");
        return dailyReportRepository.saveAndFlush(report);
    }

    private ReportPeriod period(LocalDate reportDate) {
        return new ReportPeriod(
                reportDate.minusDays(1).atTime(19, 0),
                reportDate.atTime(19, 0),
                reportDate);
    }
}
