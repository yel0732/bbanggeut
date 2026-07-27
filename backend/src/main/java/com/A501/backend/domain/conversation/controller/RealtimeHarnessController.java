package com.A501.backend.domain.conversation.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Hidden;

@Hidden
@RestController
@Profile("local")
public class RealtimeHarnessController {

    private static final Resource HARNESS_PAGE =
            new ClassPathResource("harness/realtime-harness.html");

    /**
     * FastAPI Signaling(ws://localhost:8000)은 Spring(8080)과 다른 Origin이라
     * {@code connect-src 'self'}만으로는 접속할 수 없다 — 로컬 전용 도구라 이 Origin을
     * 직접 명시한다(jira/plan/DIARY-2-W06.md §11-B).
     */
    private static final String CONTENT_SECURITY_POLICY = String.join(
            "; ",
            "default-src 'self'",
            "connect-src 'self' http://localhost:8000 ws://localhost:8000",
            "media-src 'self' blob:",
            "img-src 'self' data:",
            "style-src 'self' 'unsafe-inline'",
            "script-src 'self' 'unsafe-inline'",
            "base-uri 'none'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    @GetMapping(value = "/realtime-harness", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> getHarnessPage() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", CONTENT_SECURITY_POLICY)
                .body(HARNESS_PAGE);
    }
}
