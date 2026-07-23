package com.A501.backend.domain.game.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.A501.backend.domain.game.dto.request.SaveGameResultRequest;
import com.A501.backend.domain.game.dto.response.GameSessionResponse;
import com.A501.backend.domain.game.service.GameSessionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Game Session", description = "정상 완료된 게임 세션 결과 API")
@RestController
@RequestMapping("/api/game-sessions")
@RequiredArgsConstructor
public class GameSessionController {

    private final GameSessionService gameSessionService;

    @Operation(summary = "게임 완료 결과 저장")
    @PostMapping
    public ResponseEntity<GameSessionResponse> saveCompletedResult(
            @Valid @RequestBody SaveGameResultRequest request) {
        GameSessionResponse response = gameSessionService.saveCompletedResult(request);

        return ResponseEntity.created(URI.create("/api/game-sessions/" + response.gameSessionId()))
                .body(response);
    }
}
