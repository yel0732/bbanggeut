package com.A501.backend.domain.conversation.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.A501.backend.domain.conversation.dto.request.CompleteConversationRequest;
import com.A501.backend.domain.conversation.dto.response.CompleteConversationResponse;
import com.A501.backend.domain.conversation.dto.response.CreateConversationResponse;
import com.A501.backend.domain.conversation.dto.response.ConversationSessionResponse;
import com.A501.backend.domain.conversation.dto.response.ConversationTurnListResponse;
import com.A501.backend.domain.conversation.service.ConversationSessionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;

@Tag(name = "Conversation Session", description = "대화 세션 API")
@Validated
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ConversationSessionController {

    private final ConversationSessionService conversationSessionService;

    @Operation(summary = "대화 세션 생성")
    @PostMapping("/children/{childId}/conversations")
    public ResponseEntity<CreateConversationResponse> create(
            @Positive @PathVariable Long childId) {
        CreateConversationResponse response = conversationSessionService.create(childId);

        return ResponseEntity
                .created(URI.create("/api/v1/conversations/" + response.conversationId()))
                .body(response);
    }

    @Operation(summary = "대화 세션 조회")
    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<ConversationSessionResponse> getConversation(
            @PathVariable String conversationId) {
        return ResponseEntity.ok(conversationSessionService.getConversation(conversationId));
    }

    @Operation(summary = "대화 턴 목록 조회")
    @GetMapping("/conversations/{conversationId}/turns")
    public ResponseEntity<ConversationTurnListResponse> getConversationTurns(
            @PathVariable String conversationId,
            @PositiveOrZero @RequestParam(defaultValue = "0") int page,
            @Min(1) @Max(100) @RequestParam(defaultValue = "50") int size,
            @Parameter(description = "화자 필터 (AI, CHILD)")
            @RequestParam(required = false) String speaker) {
        return ResponseEntity.ok(
                conversationSessionService.getConversationTurns(conversationId, page, size, speaker));
    }

    @Operation(summary = "대화 세션 종료")
    @PostMapping("/conversations/{conversationId}/complete")
    public ResponseEntity<CompleteConversationResponse> completeConversation(
            @PathVariable String conversationId,
            @Valid @RequestBody CompleteConversationRequest request) {
        return ResponseEntity.ok(conversationSessionService.completeConversation(conversationId, request));
    }
}
