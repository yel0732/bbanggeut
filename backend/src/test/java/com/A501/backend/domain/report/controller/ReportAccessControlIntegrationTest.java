package com.A501.backend.domain.report.controller;

import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.report.entity.DailyReport;
import com.A501.backend.domain.report.exception.ReportApiErrorCode;
import com.A501.backend.domain.report.model.ReportPeriod;
import com.A501.backend.domain.report.port.ReportChildAccessVerifier;
import com.A501.backend.domain.report.repository.DailyReportRepository;
import com.A501.backend.domain.report.repository.ReportManualRegenerationRepository;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@SpringBootTest(properties = {
        "report.schedule.enabled=false",
        "app.jwt.secret=report-access-control-test-secret-key-1234567890"
})
@AutoConfigureMockMvc
class ReportAccessControlIntegrationTest {

    private static final Long PARENT_ID = 1L;
    private static final Long OTHER_PARENT_ID = 2L;
    private static final Long CHILD_ID = 15L;
    private static final LocalDate REPORT_DATE =
            LocalDate.of(2026, 7, 23);
    private static final String SECRET =
            "report-access-control-test-secret-key-1234567890";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DailyReportRepository dailyReportRepository;

    @Autowired
    private ReportManualRegenerationRepository regenerationRepository;

    @MockitoBean
    private ReportChildAccessVerifier childAccessVerifier;

    @BeforeEach
    void setUp() {
        regenerationRepository.deleteAll();
        dailyReportRepository.deleteAll();
    }

    @Test
    void missingAccessTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/children/{childId}/reports", CHILD_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error")
                        .value("REPORT_UNAUTHORIZED"));
    }

    @Test
    void anotherParentsChildIsHiddenAsNotFound() throws Exception {
        denyChildAccess(OTHER_PARENT_ID);

        mockMvc.perform(get("/api/children/{childId}/reports", CHILD_ID)
                        .with(parentBearer(OTHER_PARENT_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CHILD_NOT_FOUND"));
    }

    @Test
    void deletedChildIsHiddenAsNotFound() throws Exception {
        denyChildAccess(PARENT_ID);

        mockMvc.perform(get("/api/children/{childId}/reports", CHILD_ID)
                        .with(parentBearer(PARENT_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CHILD_NOT_FOUND"));
    }

    @Test
    void reportForAnotherChildIsNotExposed() throws Exception {
        dailyReportRepository.saveAndFlush(DailyReport.pending(
                99L,
                period()));

        mockMvc.perform(get(
                        "/api/children/{childId}/reports/{reportDate}",
                        CHILD_ID,
                        REPORT_DATE)
                        .with(parentBearer(PARENT_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("REPORT_NOT_FOUND"));
    }

    @Test
    void regenerationAlsoRequiresAuthentication() throws Exception {
        mockMvc.perform(post(
                        "/api/children/{childId}/reports/{reportDate}/regenerate",
                        CHILD_ID,
                        REPORT_DATE)
                        .header("Idempotency-Key", "idor-regeneration"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error")
                        .value("REPORT_UNAUTHORIZED"));
    }

    @Test
    void authenticatedOwnerCanAccessReportList() throws Exception {
        mockMvc.perform(get("/api/children/{childId}/reports", CHILD_ID)
                        .with(parentBearer(PARENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.childId").value(CHILD_ID))
                .andExpect(jsonPath("$.reports").isArray());
    }

    private void denyChildAccess(Long parentId) {
        doThrow(new BusinessException(ReportApiErrorCode.CHILD_NOT_FOUND))
                .when(childAccessVerifier)
                .verifyOwnership(parentId, CHILD_ID);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor
            parentBearer(Long parentId) {
        return request -> {
            request.addHeader(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + accessToken(parentId));
            return request;
        };
    }

    private String accessToken(Long parentId) {
        SecretKey key = Keys.hmacShaKeyFor(
                SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(parentId))
                .claim("purpose", "ACCESS")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(key)
                .compact();
    }

    private ReportPeriod period() {
        return new ReportPeriod(
                REPORT_DATE.minusDays(1).atTime(19, 0),
                REPORT_DATE.atTime(19, 0),
                REPORT_DATE);
    }
}
