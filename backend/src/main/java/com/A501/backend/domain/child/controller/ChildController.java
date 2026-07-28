package com.A501.backend.domain.child.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.A501.backend.domain.child.dto.request.CreateChildRequest;
import com.A501.backend.domain.child.dto.request.DeleteChildRequest;
import com.A501.backend.domain.child.dto.request.UpdateChildRequest;
import com.A501.backend.domain.child.dto.response.ChildNameCheckResponse;
import com.A501.backend.domain.child.dto.response.ChildResponse;
import com.A501.backend.domain.child.service.ChildService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@Tag(name = "children")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/children")
public class ChildController {

    private final ChildService childService;

    @Operation(summary = "자녀 닉네임 중복 확인")
    @GetMapping("/name/check")
    public ResponseEntity<ChildNameCheckResponse> checkName(
            Authentication authentication, @RequestParam @NotBlank @Size(max = 15) String name) {
        Long parentId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ChildNameCheckResponse.of(childService.isNameAvailable(parentId, name)));
    }

    @Operation(summary = "자녀 프로필 생성")
    @PostMapping
    public ResponseEntity<ChildResponse> create(
            Authentication authentication, @Valid @RequestBody CreateChildRequest request) {
        Long parentId = (Long) authentication.getPrincipal();
        ChildResponse child = childService.createChild(parentId, request.name(), request.imageKey());
        return ResponseEntity.status(HttpStatus.CREATED).body(child);
    }

    @Operation(summary = "자녀 프로필 목록 조회")
    @GetMapping
    public ResponseEntity<List<ChildResponse>> list(Authentication authentication) {
        Long parentId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(childService.listChildren(parentId));
    }

    @Operation(summary = "자녀 프로필 수정")
    @PatchMapping("/{childId}")
    public ResponseEntity<ChildResponse> update(
            Authentication authentication,
            @PathVariable Long childId,
            @Valid @RequestBody UpdateChildRequest request) {
        Long parentId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(childService.updateChild(parentId, childId, request.name(), request.imageKey()));
    }

    @Operation(summary = "자녀 프로필 삭제")
    @DeleteMapping("/{childId}")
    public ResponseEntity<Void> delete(
            Authentication authentication,
            @PathVariable Long childId,
            @Valid @RequestBody DeleteChildRequest request) {
        Long parentId = (Long) authentication.getPrincipal();
        childService.deleteChild(parentId, childId, request.pin());
        return ResponseEntity.noContent().build();
    }
}
