package com.A501.backend.domain.conversation.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.A501.backend.domain.conversation.dto.request.CreateConversationRequest;
import com.A501.backend.domain.conversation.dto.response.ConversationHarnessStateResponse;
import com.A501.backend.domain.conversation.dto.response.CreateConversationResponse;
import com.A501.backend.domain.conversation.service.ConversationHarnessService;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Hidden
@RestController
@Profile("local")
@RequiredArgsConstructor
public class ConversationHarnessController {

    private static final Resource HARNESS_PAGE =
            new ClassPathResource("harness/conversation-harness.html");
    private static final String CONTENT_SECURITY_POLICY = String.join(
            "; ",
            "default-src 'self'",
            "connect-src 'self'",
            "img-src 'self' data:",
            "style-src 'self' 'unsafe-inline'",
            "script-src 'self' 'unsafe-inline'",
            "base-uri 'none'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    private final ConversationHarnessService conversationHarnessService;

    @GetMapping(value = "/harness", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> getHarnessPage() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", CONTENT_SECURITY_POLICY)
                .body(HARNESS_PAGE);
    }

    @PostMapping("/harness/api/children/{childId}/conversations")
    public ResponseEntity<CreateConversationResponse> createOrResume(
            @PathVariable Long childId,
            @Valid @RequestBody CreateConversationRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(conversationHarnessService.createOrResume(
                        childId,
                        request));
    }

    @GetMapping("/harness/api/conversations/{conversationId}/state")
    public ResponseEntity<ConversationHarnessStateResponse> getState(
            @PathVariable String conversationId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(conversationHarnessService.getState(conversationId));
    }
}
