package com.A501.backend.domain.childdiary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.childdiary.entity.ChildDiaryGenerationMethod;
import com.A501.backend.domain.childdiary.model.ChildDiaryDraft;
import com.A501.backend.domain.childdiary.model.ChildDiaryPreviewProperties;
import tools.jackson.databind.ObjectMapper;

class ChildDiaryPreviewTokenServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-27T10:00:00Z");
    private final ChildDiaryPreviewProperties properties = new ChildDiaryPreviewProperties(
            "test-child-diary-preview-secret-1234567890", Duration.ofMinutes(10));
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void verifiesAnIssuedToken() {
        ChildDiaryPreviewTokenService service = serviceAt(NOW);
        ChildDiaryDraft draft = draft(NOW.plusSeconds(600).getEpochSecond());
        assertThat(service.verify(service.issue(draft))).isEqualTo(draft);
    }

    @Test
    void rejectsTamperedAndExpiredTokens() {
        ChildDiaryPreviewTokenService service = serviceAt(NOW);
        String token = service.issue(draft(NOW.plusSeconds(600).getEpochSecond()));
        assertThatThrownBy(() -> service.verify(token + "x")).isInstanceOf(BusinessException.class);

        String expired = service.issue(draft(NOW.minusSeconds(1).getEpochSecond()));
        assertThatThrownBy(() -> service.verify(expired)).isInstanceOf(BusinessException.class);
    }

    private ChildDiaryPreviewTokenService serviceAt(Instant instant) {
        return new ChildDiaryPreviewTokenService(objectMapper, properties,
                Clock.fixed(instant, ZoneOffset.UTC));
    }
    private ChildDiaryDraft draft(long expiresAt) {
        return new ChildDiaryDraft(1L, 15L, "conversation-1", LocalDate.of(2026, 7, 27),
                "오늘의 일기", List.of("친구와 놀았다.", "즐거웠다.", "그 순간이 기억에 남았다."),
                ChildDiaryGenerationMethod.AI, "gpt-5-mini", "child-diary-v3", expiresAt);
    }
}
