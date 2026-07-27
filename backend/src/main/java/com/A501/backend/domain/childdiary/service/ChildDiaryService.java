package com.A501.backend.domain.childdiary.service;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.childdiary.dto.response.ChildDiaryListResponse;
import com.A501.backend.domain.childdiary.dto.response.ChildDiaryPreviewResponse;
import com.A501.backend.domain.childdiary.dto.response.ChildDiaryResponse;
import com.A501.backend.domain.childdiary.entity.ChildDiary;
import com.A501.backend.domain.childdiary.entity.ChildDiaryGenerationMethod;
import com.A501.backend.domain.childdiary.exception.ChildDiaryErrorCode;
import com.A501.backend.domain.childdiary.model.ChildDiaryAiRequest;
import com.A501.backend.domain.childdiary.model.ChildDiaryAiResponse;
import com.A501.backend.domain.childdiary.model.ChildDiaryDraft;
import com.A501.backend.domain.childdiary.port.ChildDiaryAiPort;
import com.A501.backend.domain.childdiary.port.ChildDiaryChildAccessVerifier;
import com.A501.backend.domain.childdiary.port.ChildDiaryCurrentParentProvider;
import com.A501.backend.domain.childdiary.repository.ChildDiaryRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChildDiaryService {
    private static final String TITLE = "오늘의 일기";
    private final ChildDiaryRepository repository;
    private final ChildDiarySourceService sourceService;
    private final ChildDiaryAiPort aiPort;
    private final RuleBasedChildDiaryGenerator fallbackGenerator;
    private final ChildDiaryPreviewTokenService previewTokenService;
    private final ChildDiaryCurrentParentProvider currentParentProvider;
    private final ChildDiaryChildAccessVerifier childAccessVerifier;
    private final ObjectMapper objectMapper;

    public ChildDiaryPreviewResponse preview(Long childId, String conversationId) {
        Long parentId = authorize(childId);
        if (repository.existsByConversationId(conversationId)) throw alreadySaved();
        ChildDiarySourceService.Source source = sourceService.getSource(childId, conversationId);
        Generated generated = generate(new ChildDiaryAiRequest(
                conversationId, source.diaryDate(), source.topics()));
        ChildDiaryDraft draft = new ChildDiaryDraft(parentId, childId, conversationId,
                source.diaryDate(), TITLE, generated.lines(), generated.method(),
                generated.modelVersion(), generated.promptVersion(),
                previewTokenService.expirationEpochSecond());
        return new ChildDiaryPreviewResponse(conversationId, source.diaryDate(), TITLE,
                draft.lines(), previewTokenService.issue(draft));
    }

    @Transactional
    public ChildDiaryResponse save(Long childId, String previewToken) {
        Long parentId = authorize(childId);
        ChildDiaryDraft draft = previewTokenService.verify(previewToken);
        if (!parentId.equals(draft.parentId()) || !childId.equals(draft.childId())
                || !TITLE.equals(draft.title())) {
            throw new BusinessException(ChildDiaryErrorCode.PREVIEW_INVALID);
        }
        if (repository.existsByConversationId(draft.conversationId())) throw alreadySaved();
        ChildDiary diary = ChildDiary.create(childId, draft.conversationId(), draft.diaryDate(),
                draft.title(), writeLines(draft.lines()), draft.generationMethod(),
                draft.modelVersion(), draft.promptVersion());
        try {
            return toResponse(repository.saveAndFlush(diary));
        } catch (DataIntegrityViolationException exception) {
            throw alreadySaved();
        }
    }

    public ChildDiaryListResponse getList(Long childId, int page, int size) {
        authorize(childId);
        Page<ChildDiary> diaries = repository
                .findAllByChildIdOrderByDiaryDateDescCreatedAtDesc(childId, PageRequest.of(page, size));
        return new ChildDiaryListResponse(diaries.stream().map(this::toResponse).toList(),
                page, size, diaries.getTotalElements(), diaries.getTotalPages());
    }

    public ChildDiaryResponse getDetail(Long childId, Long diaryId) {
        authorize(childId);
        return repository.findByChildDiaryIdAndChildId(diaryId, childId).map(this::toResponse)
                .orElseThrow(() -> new BusinessException(ChildDiaryErrorCode.NOT_FOUND));
    }

    private Generated generate(ChildDiaryAiRequest request) {
        try {
            ChildDiaryAiResponse response = aiPort.generate(request);
            return new Generated(response.lines(), ChildDiaryGenerationMethod.AI,
                    response.modelVersion(), response.promptVersion());
        } catch (ChildDiaryAiUnavailableException exception) {
            return new Generated(fallbackGenerator.generate(request.topics()),
                    ChildDiaryGenerationMethod.RULE_BASED, null, "child-diary-rule-v1");
        }
    }

    private Long authorize(Long childId) {
        Long parentId = currentParentProvider.getCurrentParentId();
        childAccessVerifier.verifyOwnership(parentId, childId);
        return parentId;
    }

    private ChildDiaryResponse toResponse(ChildDiary diary) {
        return new ChildDiaryResponse(diary.getChildDiaryId(), diary.getChildId(),
                diary.getConversationId(), diary.getDiaryDate(), diary.getTitle(),
                readLines(diary.getLinesJson()), diary.getCreatedAt());
    }

    private String writeLines(List<String> lines) {
        try { return objectMapper.writeValueAsString(lines); }
        catch (JacksonException exception) { throw new IllegalStateException(exception); }
    }
    private List<String> readLines(String json) {
        try { return objectMapper.readValue(json, new TypeReference<>() {}); }
        catch (JacksonException exception) { throw new IllegalStateException(exception); }
    }
    private BusinessException alreadySaved() {
        return new BusinessException(ChildDiaryErrorCode.ALREADY_SAVED);
    }
    private record Generated(List<String> lines, ChildDiaryGenerationMethod method,
            String modelVersion, String promptVersion) {}
}
