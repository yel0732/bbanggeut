package com.A501.backend.domain.report.controller;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.report.exception.ReportApiErrorCode;
import com.A501.backend.domain.report.dto.request.ReportNotificationPreferenceRequest;
import com.A501.backend.domain.report.dto.response.ReportNotificationPreferenceResponse;
import com.A501.backend.domain.report.port.ReportCurrentParentProvider;
import com.A501.backend.domain.report.service.ReportNotificationPreferenceService;
import com.A501.backend.domain.report.service.ReportNotificationQueryService;
import com.A501.backend.domain.report.dto.response.UnreadReportNotificationsResponse;
import com.A501.backend.domain.report.sse.ReportNotificationStreamService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Report Notifications")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class ReportNotificationController {

    private final ReportNotificationStreamService streamService;
    private final ObjectProvider<ReportCurrentParentProvider>
            currentParentProvider;
    private final ReportNotificationPreferenceService preferenceService;
    private final ReportNotificationQueryService notificationQueryService;

    @Operation(summary = "Get available unread report notifications")
    @GetMapping("/unread")
    public UnreadReportNotificationsResponse unread() {
        return notificationQueryService.getAvailableUnread(
                requiredParentProvider().getCurrentParentId());
    }

    @Operation(summary = "Mark a report notification as read")
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markRead(
            @PathVariable Long notificationId) {
        notificationQueryService.markRead(
                requiredParentProvider().getCurrentParentId(),
                notificationId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Mark the notification for a viewed report as read")
    @PatchMapping("/reports/{reportId}/read")
    public ResponseEntity<Void> markReportRead(
            @PathVariable Long reportId) {
        notificationQueryService.markReportRead(
                requiredParentProvider().getCurrentParentId(),
                reportId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get report notification preference")
    @GetMapping("/preferences")
    public ReportNotificationPreferenceResponse preference() {
        return preferenceService.getOrCreate(
                requiredParentProvider().getCurrentParentId());
    }

    @Operation(summary = "Update report notification preference")
    @PutMapping("/preferences")
    public ReportNotificationPreferenceResponse updatePreference(
            @Valid @RequestBody ReportNotificationPreferenceRequest request
    ) {
        return preferenceService.update(
                requiredParentProvider().getCurrentParentId(),
                request.enabled(),
                request.time(),
                request.timezone());
    }

    @Operation(summary = "Stream report notifications over SSE")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestHeader(value = "Last-Event-ID", required = false)
            String lastEventId
    ) {
        Long parentId = requiredParentProvider().getCurrentParentId();
        return streamService.subscribe(parentId, parseLastEventId(lastEventId));
    }

    private ReportCurrentParentProvider requiredParentProvider() {
        ReportCurrentParentProvider provider =
                currentParentProvider.getIfAvailable();
        if (provider == null) {
            throw new BusinessException(ReportApiErrorCode.UNAUTHORIZED);
        }
        return provider;
    }

    private Long parseLastEventId(String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(lastEventId.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
