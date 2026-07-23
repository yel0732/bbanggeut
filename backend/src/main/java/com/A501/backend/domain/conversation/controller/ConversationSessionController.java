package com.A501.backend.domain.conversation.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.A501.backend.domain.conversation.dto.request.CreateConversationRequest;
import com.A501.backend.domain.conversation.dto.response.CreateConversationResponse;
import com.A501.backend.domain.conversation.dto.response.ConversationSessionResponse;
import com.A501.backend.domain.conversation.service.ConversationSessionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
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
            @Positive @PathVariable Long childId,
            @Valid @RequestBody CreateConversationRequest request) {
        CreateConversationResponse response = conversationSessionService.create(childId, request);

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
}
