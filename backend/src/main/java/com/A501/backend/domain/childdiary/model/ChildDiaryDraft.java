package com.A501.backend.domain.childdiary.model;

import java.time.LocalDate;
import java.util.List;

import com.A501.backend.domain.childdiary.entity.ChildDiaryGenerationMethod;

public record ChildDiaryDraft(Long parentId, Long childId, String conversationId,
        LocalDate diaryDate, String title, List<String> lines,
        ChildDiaryGenerationMethod generationMethod, String modelVersion,
        String promptVersion, long expiresAtEpochSecond) {}
