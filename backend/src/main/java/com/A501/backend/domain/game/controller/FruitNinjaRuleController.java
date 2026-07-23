package com.A501.backend.domain.game.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.A501.backend.domain.game.dto.response.FruitNinjaRuleResponse;
import com.A501.backend.domain.game.service.FruitNinjaRuleService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Fruit Ninja Rule", description = "후르츠 게임 규칙 API")
@RestController
@RequestMapping("/api/games/fruit-ninja/rules")
@RequiredArgsConstructor
public class FruitNinjaRuleController {

    private final FruitNinjaRuleService fruitNinjaRuleService;

    @Operation(summary = "활성 후르츠 게임 규칙 조회")
    @GetMapping("/active")
    public ResponseEntity<FruitNinjaRuleResponse> getActiveRule() {
        return ResponseEntity.ok(fruitNinjaRuleService.getActiveRule());
    }
}
