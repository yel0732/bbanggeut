package com.A501.backend.domain.report.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.A501.backend.domain.report.dto.response.ReportGenerationResponse;
import com.A501.backend.domain.report.service.AdminHistoricalReportGenerationService;

import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequestMapping("/api/admin/children/{childId}/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final AdminHistoricalReportGenerationService generationService;

    @PostMapping("/{reportDate}/generate")
    public ResponseEntity<ReportGenerationResponse> generate(
            Authentication authentication,
            @Positive @PathVariable Long childId,
            @PathVariable
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate reportDate) {
        Long adminParentId = (Long) authentication.getPrincipal();
        return ResponseEntity.accepted().body(generationService.generate(
                adminParentId, childId, reportDate));
    }
}
