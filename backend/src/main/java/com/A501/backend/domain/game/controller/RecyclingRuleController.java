package com.A501.backend.domain.game.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.A501.backend.domain.game.dto.response.RecyclingRuleResponse;
import com.A501.backend.domain.game.service.RecyclingRuleService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Recycling Rule", description = "분리수거 게임 규칙 API")
@RestController
@RequestMapping("/api/games/recycling/rules")
@RequiredArgsConstructor
public class RecyclingRuleController {

    private final RecyclingRuleService recyclingRuleService;

    @Operation(summary = "활성 분리수거 게임 규칙과 쓰레기 목록 조회")
    @GetMapping("/active")
    public ResponseEntity<RecyclingRuleResponse> getActiveRule() {
        return ResponseEntity.ok(recyclingRuleService.getActiveRule());
    }
}
