package com.A501.backend.domain.report.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.A501.backend.domain.report.dto.response.ReportRegenerationResponse;
import com.A501.backend.domain.report.service.ReportManualRegenerationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Reports")
@RestController
@RequestMapping("/api/children/{childId}/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportManualRegenerationService regenerationService;

    @Operation(summary = "Regenerate a failed daily report")
    @PostMapping("/{reportDate}/regenerate")
    public ResponseEntity<ReportRegenerationResponse> regenerate(
            @PathVariable Long childId,
            @PathVariable
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate reportDate,
            @RequestHeader(value = "Idempotency-Key", required = false)
            String idempotencyKey,
            @RequestHeader(
                    value = "X-Parent-Access-Token",
                    required = false)
            String parentAccessToken
    ) {
        return ResponseEntity.accepted().body(
                regenerationService.request(
                        childId,
                        reportDate,
                        idempotencyKey,
                        parentAccessToken));
    }
}
