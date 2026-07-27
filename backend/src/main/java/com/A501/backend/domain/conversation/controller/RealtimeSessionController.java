package com.A501.backend.domain.conversation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.A501.backend.domain.conversation.dto.response.RealtimeSessionResponse;
import com.A501.backend.domain.conversation.service.RealtimeSessionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Realtime Session", description = "Realtime Session 발급 API")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RealtimeSessionController {

    private final RealtimeSessionService realtimeSessionService;

    @Operation(summary = "Realtime Session 발급")
    @PostMapping("/conversations/{conversationId}/realtime-session")
    public ResponseEntity<RealtimeSessionResponse> issueRealtimeSession(
            @PathVariable String conversationId) {
        return ResponseEntity.ok(realtimeSessionService.issue(conversationId));
    }
}
