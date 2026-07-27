package com.A501.backend.domain.report.controller;

import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.report.entity.DailyReport;
import com.A501.backend.domain.report.exception.ReportApiErrorCode;
import com.A501.backend.domain.report.model.ReportPeriod;
import com.A501.backend.domain.report.port.ReportChildAccessVerifier;
import com.A501.backend.domain.report.port.ReportCurrentParentProvider;
import com.A501.backend.domain.report.port.ReportParentAccessTokenVerifier;
import com.A501.backend.domain.report.repository.DailyReportRepository;
import com.A501.backend.domain.report.repository.ReportManualRegenerationRepository;

@SpringBootTest(properties = "report.schedule.enabled=false")
@AutoConfigureMockMvc
class ReportControllerTest {

    private static final Long PARENT_ID = 1L;
    private static final Long CHILD_ID = 15L;
    private static final LocalDate REPORT_DATE = LocalDate.of(2026, 7, 23);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DailyReportRepository dailyReportRepository;

    @Autowired
    private ReportManualRegenerationRepository regenerationRepository;

    @MockitoBean
    private ReportCurrentParentProvider currentParentProvider;

    @MockitoBean
    private ReportChildAccessVerifier childAccessVerifier;

    @MockitoBean
    private ReportParentAccessTokenVerifier parentAccessTokenVerifier;

    @BeforeEach
    void setUp() {
        regenerationRepository.deleteAll();
        dailyReportRepository.deleteAll();
        org.mockito.Mockito.when(currentParentProvider.getCurrentParentId())
                .thenReturn(PARENT_ID);
    }

    @Test
    void failedReportCanBeRegisteredForRegeneration() throws Exception {
        DailyReport report = createFailedReport();

        mockMvc.perform(post(
                        "/api/children/{childId}/reports/{reportDate}/regenerate",
                        CHILD_ID,
                        REPORT_DATE)
                        .header("Idempotency-Key", "retry-20260723")
                        .header("X-Parent-Access-Token", "parent-token"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.reportId").value(report.getId()))
                .andExpect(jsonPath("$.childId").value(CHILD_ID))
                .andExpect(jsonPath("$.reportDate").value("2026-07-23"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.requestedAt").exists());

        org.assertj.core.api.Assertions.assertThat(regenerationRepository.count())
                .isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(
                        dailyReportRepository.findById(report.getId())
                                .orElseThrow()
                                .getGenerationStatus())
                .isIn(
                        com.A501.backend.domain.report.entity.ReportGenerationStatus.PENDING,
                        com.A501.backend.domain.report.entity.ReportGenerationStatus.PROCESSING,
                        com.A501.backend.domain.report.entity.ReportGenerationStatus.FAILED);
    }

    @Test
    void sameIdempotencyKeyReturnsOriginalRequest() throws Exception {
        createFailedReport();

        for (int request = 0; request < 2; request++) {
            mockMvc.perform(post(
                            "/api/children/{childId}/reports/{reportDate}/regenerate",
                            CHILD_ID,
                            REPORT_DATE)
                            .header("Idempotency-Key", "same-key")
                            .header("X-Parent-Access-Token", "parent-token"))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("PENDING"));
        }

        org.assertj.core.api.Assertions.assertThat(regenerationRepository.count())
                .isEqualTo(1);
    }

    @Test
    void nonFailedReportIsRejected() throws Exception {
        dailyReportRepository.saveAndFlush(DailyReport.pending(
                CHILD_ID,
                period()));

        mockMvc.perform(post(
                        "/api/children/{childId}/reports/{reportDate}/regenerate",
                        CHILD_ID,
                        REPORT_DATE)
                        .header("Idempotency-Key", "not-failed")
                        .header("X-Parent-Access-Token", "parent-token"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error")
                        .value("REPORT_REGENERATION_NOT_ALLOWED"));
    }

    @Test
    void childOwnershipFailureIsHiddenAsNotFound() throws Exception {
        doThrow(new BusinessException(ReportApiErrorCode.CHILD_NOT_FOUND))
                .when(childAccessVerifier)
                .verifyOwnership(PARENT_ID, CHILD_ID);

        mockMvc.perform(post(
                        "/api/children/{childId}/reports/{reportDate}/regenerate",
                        CHILD_ID,
                        REPORT_DATE)
                        .header("Idempotency-Key", "forbidden-child")
                        .header("X-Parent-Access-Token", "parent-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CHILD_NOT_FOUND"));
    }

    @Test
    void missingIdempotencyKeyIsBadRequest() throws Exception {
        mockMvc.perform(post(
                        "/api/children/{childId}/reports/{reportDate}/regenerate",
                        CHILD_ID,
                        REPORT_DATE)
                        .header("X-Parent-Access-Token", "parent-token"))
                .andExpect(status().isBadRequest());
    }

    private DailyReport createFailedReport() {
        DailyReport report = DailyReport.pending(CHILD_ID, period());
        report.startProcessing(LocalDateTime.of(2026, 7, 23, 19, 1));
        report.fail(
                LocalDateTime.of(2026, 7, 23, 19, 2),
                "generation failed");
        return dailyReportRepository.saveAndFlush(report);
    }

    private ReportPeriod period() {
        return new ReportPeriod(
                LocalDateTime.of(2026, 7, 22, 19, 0),
                LocalDateTime.of(2026, 7, 23, 19, 0),
                REPORT_DATE);
    }
}
