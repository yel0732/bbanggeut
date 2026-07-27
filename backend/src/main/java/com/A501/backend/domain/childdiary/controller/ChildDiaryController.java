package com.A501.backend.domain.childdiary.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.A501.backend.domain.childdiary.dto.request.SaveChildDiaryRequest;
import com.A501.backend.domain.childdiary.dto.response.ChildDiaryListResponse;
import com.A501.backend.domain.childdiary.dto.response.ChildDiaryPreviewResponse;
import com.A501.backend.domain.childdiary.dto.response.ChildDiaryResponse;
import com.A501.backend.domain.childdiary.service.ChildDiaryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;

@Tag(name = "Child Diaries")
@Validated
@RestController
@RequestMapping("/api/v1/children/{childId}")
@RequiredArgsConstructor
public class ChildDiaryController {
    private final ChildDiaryService service;

    @Operation(summary = "완료된 대화의 아이 일기 미리보기 생성")
    @PostMapping("/conversations/{conversationId}/child-diary/preview")
    public ResponseEntity<ChildDiaryPreviewResponse> preview(
            @Positive @PathVariable Long childId, @PathVariable String conversationId) {
        return ResponseEntity.ok(service.preview(childId, conversationId));
    }

    @Operation(summary = "미리보기를 아이 일기장에 붙이기")
    @PostMapping("/child-diaries")
    public ResponseEntity<ChildDiaryResponse> save(@Positive @PathVariable Long childId,
            @Valid @RequestBody SaveChildDiaryRequest request) {
        ChildDiaryResponse response = service.save(childId, request.previewToken());
        return ResponseEntity.created(URI.create("/api/v1/children/" + childId
                + "/child-diaries/" + response.childDiaryId())).body(response);
    }

    @Operation(summary = "아이 일기 목록 조회")
    @GetMapping("/child-diaries")
    public ResponseEntity<ChildDiaryListResponse> getList(@Positive @PathVariable Long childId,
            @PositiveOrZero @RequestParam(defaultValue = "0") int page,
            @Min(1) @Max(100) @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.getList(childId, page, size));
    }

    @Operation(summary = "아이 일기 상세 조회")
    @GetMapping("/child-diaries/{diaryId}")
    public ResponseEntity<ChildDiaryResponse> getDetail(@Positive @PathVariable Long childId,
            @Positive @PathVariable Long diaryId) {
        return ResponseEntity.ok(service.getDetail(childId, diaryId));
    }
}
