package com.A501.backend.domain.childdiary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.A501.backend.domain.childdiary.dto.response.ChildDiaryPreviewResponse;
import com.A501.backend.domain.childdiary.dto.response.ChildDiaryResponse;
import com.A501.backend.domain.childdiary.entity.ChildDiary;
import com.A501.backend.domain.childdiary.model.ChildDiaryAiRequest;
import com.A501.backend.domain.childdiary.model.ChildDiaryPreviewProperties;
import com.A501.backend.domain.childdiary.port.ChildDiaryAiPort;
import com.A501.backend.domain.childdiary.port.ChildDiaryChildAccessVerifier;
import com.A501.backend.domain.childdiary.port.ChildDiaryCurrentParentProvider;
import com.A501.backend.domain.childdiary.repository.ChildDiaryRepository;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ChildDiaryServiceTest {
    @Mock ChildDiaryRepository repository;
    @Mock ChildDiarySourceService sourceService;
    @Mock ChildDiaryAiPort aiPort;
    @Mock ChildDiaryCurrentParentProvider parentProvider;
    @Mock ChildDiaryChildAccessVerifier accessVerifier;
    ChildDiaryService service;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        ChildDiaryPreviewTokenService tokenService = new ChildDiaryPreviewTokenService(mapper,
                new ChildDiaryPreviewProperties(
                        "test-child-diary-preview-secret-1234567890", Duration.ofMinutes(10)));
        service = new ChildDiaryService(repository, sourceService, aiPort,
                new RuleBasedChildDiaryGenerator(), tokenService, parentProvider,
                accessVerifier, mapper);
        when(parentProvider.getCurrentParentId()).thenReturn(1L);
        doNothing().when(accessVerifier).verifyOwnership(1L, 15L);
    }

    @Test
    void fallsBackOnAiFailureAndSavesExactlyTheSignedPreview() {
        ChildDiaryAiRequest.Topic topic = new ChildDiaryAiRequest.Topic("DAILY",
                new ChildDiaryAiRequest.Slots("친구와 놀았다", List.of(), null,
                        "즐거웠다", null, null));
        when(sourceService.getSource(15L, "conversation-1"))
                .thenReturn(new ChildDiarySourceService.Source(
                        LocalDate.of(2026, 7, 27), List.of(topic)));
        when(aiPort.generate(any())).thenThrow(new ChildDiaryAiUnavailableException("down"));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ChildDiaryPreviewResponse preview = service.preview(15L, "conversation-1");
        ChildDiaryResponse saved = service.save(15L, preview.previewToken());

        assertThat(preview.title()).isEqualTo("오늘의 일기");
        assertThat(preview.lines()).hasSizeBetween(3, 4);
        assertThat(saved.lines()).isEqualTo(preview.lines());
        assertThat(saved.conversationId()).isEqualTo("conversation-1");
        verify(repository).saveAndFlush(any(ChildDiary.class));
    }
}
