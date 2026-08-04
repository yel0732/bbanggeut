package com.A501.backend.domain.report.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.A501.backend.config.CorsProperties;
import com.A501.backend.config.SecurityConfig;
import com.A501.backend.domain.auth.entity.Parent;
import com.A501.backend.domain.auth.entity.ParentRole;
import com.A501.backend.domain.auth.security.JwtAuthenticationEntryPoint;
import com.A501.backend.domain.auth.security.JwtAuthenticationFilter;
import com.A501.backend.domain.auth.security.JwtProperties;
import com.A501.backend.domain.auth.security.JwtProvider;
import com.A501.backend.domain.report.dto.response.ReportGenerationResponse;
import com.A501.backend.domain.report.entity.ReportGenerationStatus;
import com.A501.backend.domain.report.service.AdminHistoricalReportGenerationService;

@WebMvcTest(AdminReportController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class, JwtProvider.class})
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
class AdminReportControllerSecurityTest {

    private static final String PATH =
            "/api/admin/children/10/reports/2026-08-03/generate";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private AdminHistoricalReportGenerationService generationService;

    @Test
    void generate_rejectsAnonymousUser() throws Exception {
        mockMvc.perform(post(PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void generate_rejectsParentRole() throws Exception {
        mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(ParentRole.PARENT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void generate_acceptsAdminRole() throws Exception {
        LocalDate reportDate = LocalDate.of(2026, 8, 3);
        when(generationService.generate(eq(1L), eq(10L), eq(reportDate)))
                .thenReturn(new ReportGenerationResponse(
                        20L,
                        10L,
                        reportDate,
                        LocalDateTime.of(2026, 8, 2, 15, 55),
                        LocalDateTime.of(2026, 8, 3, 15, 55),
                        ReportGenerationStatus.PENDING));

        mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(ParentRole.ADMIN)))
                .andExpect(status().isAccepted());
    }

    private String token(ParentRole role) {
        Parent parent = Parent.createLocal(
                "admin@example.com", "hashed", LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(parent, "parentId", 1L);
        ReflectionTestUtils.setField(parent, "role", role);
        return jwtProvider.generateAccessToken(parent);
    }
}
