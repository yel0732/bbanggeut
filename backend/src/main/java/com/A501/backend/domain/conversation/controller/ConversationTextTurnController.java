package com.A501.backend.domain.conversation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.A501.backend.domain.conversation.dto.request.CreateTextTurnRequest;
import com.A501.backend.domain.conversation.dto.response.TextTurnResponse;
import com.A501.backend.domain.conversation.service.ConversationTextTurnService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Conversation Text Turn", description = "개발용 텍스트 대화 API")
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationTextTurnController {

    private final ConversationTextTurnService conversationTextTurnService;

    @Operation(summary = "텍스트 대화 턴 처리")
    @PostMapping("/{conversationId}/text-turns")
    public ResponseEntity<TextTurnResponse> createTextTurn(
            @PathVariable String conversationId,
            @Valid @RequestBody CreateTextTurnRequest request) {
        return ResponseEntity.ok(
                conversationTextTurnService.createTextTurn(
                        conversationId,
                        request));
    }
}
