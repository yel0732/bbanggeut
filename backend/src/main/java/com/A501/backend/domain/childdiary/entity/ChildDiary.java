package com.A501.backend.domain.childdiary.entity;

import java.time.LocalDate;

import com.A501.backend.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "child_diary",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_child_diary_conversation", columnNames = "conversation_id"),
        indexes = @Index(
                name = "idx_child_diary_child_date", columnList = "child_id, diary_date"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChildDiary extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "child_diary_id")
    private Long childDiaryId;

    @Column(name = "child_id", nullable = false)
    private Long childId;

    @Column(name = "conversation_id", nullable = false, length = 40)
    private String conversationId;

    @Column(name = "diary_date", nullable = false)
    private LocalDate diaryDate;

    @Column(name = "title", nullable = false, length = 20)
    private String title;

    @Column(name = "lines_json", nullable = false, columnDefinition = "TEXT")
    private String linesJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "generation_method", nullable = false, length = 20)
    private ChildDiaryGenerationMethod generationMethod;

    @Column(name = "model_version", length = 100)
    private String modelVersion;

    @Column(name = "prompt_version", nullable = false, length = 100)
    private String promptVersion;

    private ChildDiary(Long childId, String conversationId, LocalDate diaryDate,
            String title, String linesJson, ChildDiaryGenerationMethod generationMethod,
            String modelVersion, String promptVersion) {
        this.childId = childId;
        this.conversationId = conversationId;
        this.diaryDate = diaryDate;
        this.title = title;
        this.linesJson = linesJson;
        this.generationMethod = generationMethod;
        this.modelVersion = modelVersion;
        this.promptVersion = promptVersion;
    }

    public static ChildDiary create(Long childId, String conversationId, LocalDate diaryDate,
            String title, String linesJson, ChildDiaryGenerationMethod generationMethod,
            String modelVersion, String promptVersion) {
        return new ChildDiary(childId, conversationId, diaryDate, title, linesJson,
                generationMethod, modelVersion, promptVersion);
    }
}
