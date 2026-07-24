package com.A501.backend.domain.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.A501.backend.domain.auth.dto.request.SetPinRequest;
import com.A501.backend.domain.auth.service.PinService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "profile")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/profile")
public class PinController {

    private final PinService pinService;

    @Operation(summary = "PIN 최초 설정")
    @PostMapping("/pin")
    public ResponseEntity<Void> setPin(Authentication authentication, @Valid @RequestBody SetPinRequest request) {
        Long parentId = (Long) authentication.getPrincipal();
        pinService.setPin(parentId, request.pin());
        return ResponseEntity.ok().build();
    }
}
