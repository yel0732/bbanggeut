package com.A501.backend.domain.auth.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.A501.backend.domain.auth.dto.request.UpdateProfileImageRequest;
import com.A501.backend.domain.auth.dto.response.ProfileImageResponse;
import com.A501.backend.domain.auth.dto.response.ProfileResponse;
import com.A501.backend.domain.auth.service.ProfileService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "profile")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    @Operation(summary = "부모 프로필 조회 (프로필 사진 · PIN 설정 여부)")
    @GetMapping
    public ResponseEntity<ProfileResponse> getProfile(Authentication authentication) {
        Long parentId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(profileService.getProfile(parentId));
    }

    @Operation(summary = "부모 프로필 사진 변경 (프리셋 선택)")
    @PatchMapping("/image")
    public ResponseEntity<ProfileImageResponse> updateProfileImage(
            Authentication authentication, @Valid @RequestBody UpdateProfileImageRequest request) {
        Long parentId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(profileService.updateProfileImage(parentId, request.imageKey()));
    }

    @Operation(summary = "부모 프로필 사진 업로드 (웹캠 촬영)")
    @PostMapping(value = "/image/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProfileImageResponse> uploadProfileImage(
            Authentication authentication, @RequestPart("file") MultipartFile file) {
        Long parentId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(profileService.uploadProfileImage(parentId, file));
    }
}
