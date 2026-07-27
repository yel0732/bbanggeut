package com.A501.backend.domain.child.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.A501.backend.domain.child.dto.response.ChildNameCheckResponse;
import com.A501.backend.domain.child.service.ChildService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
}
