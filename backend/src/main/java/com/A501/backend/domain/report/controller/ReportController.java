package com.A501.backend.domain.report.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.A501.backend.domain.report.dto.response.ReportDetailResponse;
import com.A501.backend.domain.report.dto.response.ReportGenerationResponse;
import com.A501.backend.domain.report.dto.response.ReportListResponse;
import com.A501.backend.domain.report.dto.response.ReportRegenerationResponse;
import com.A501.backend.domain.report.entity.ReportGenerationStatus;
import com.A501.backend.domain.report.service.ReportManualRegenerationService;
import com.A501.backend.domain.report.service.ReportManualGenerationService;
import com.A501.backend.domain.report.service.ReportQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;

@Tag(name = "Reports")
@Validated
@RestController
@RequestMapping("/api/children/{childId}/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportManualRegenerationService regenerationService;
    private final ReportManualGenerationService manualGenerationService;
    private final ReportQueryService queryService;

    @Operation(summary = "Get daily reports for a child")
    @GetMapping
    public ResponseEntity<ReportListResponse> getReports(
            @Positive @PathVariable Long childId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,
            @RequestParam(required = false)
            ReportGenerationStatus status,
            @PositiveOrZero @RequestParam(defaultValue = "0") int page,
            @Min(1) @Max(100)
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(queryService.getReports(
                childId,
                startDate,
                endDate,
                status,
                page,
                size));
    }

    @Operation(summary = "Get a daily report by date")
    @GetMapping("/{reportDate}")
    public ResponseEntity<ReportDetailResponse> getReport(
            @Positive @PathVariable Long childId,
            @PathVariable
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate reportDate
    ) {
        return ResponseEntity.ok(
                queryService.getReport(
                        childId,
                        reportDate));
    }

    @Operation(summary = "Regenerate a failed daily report")
    @PostMapping("/{reportDate}/regenerate")
    public ResponseEntity<ReportRegenerationResponse> regenerate(
            @PathVariable Long childId,
            @PathVariable
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate reportDate,
            @RequestHeader(value = "Idempotency-Key", required = false)
            String idempotencyKey
    ) {
        return ResponseEntity.accepted().body(
                regenerationService.request(
                        childId,
                        reportDate,
                        idempotencyKey));
    }

    @Operation(summary = "Generate today's daily report immediately")
    @PostMapping("/{reportDate}/generate")
    public ResponseEntity<ReportGenerationResponse> generate(
            @Positive @PathVariable Long childId,
            @PathVariable
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate reportDate
    ) {
        return ResponseEntity.accepted().body(
                manualGenerationService.generate(childId, reportDate));
    }
}
