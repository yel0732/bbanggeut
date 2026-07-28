package com.A501.backend.domain.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.A501.backend.domain.auth.dto.request.UpdateProfileImageRequest;
import com.A501.backend.domain.auth.dto.response.ProfileImageResponse;
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

    @Operation(summary = "부모 프로필 사진 변경 (프리셋 선택)")
    @PatchMapping("/image")
    public ResponseEntity<ProfileImageResponse> updateProfileImage(
            Authentication authentication, @Valid @RequestBody UpdateProfileImageRequest request) {
        Long parentId = (Long) authentication.getPrincipal();
        String profileImageUrl = profileService.updateProfileImage(parentId, request.imageKey());
        return ResponseEntity.ok(ProfileImageResponse.of(profileImageUrl));
    }
}
