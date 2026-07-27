package com.A501.backend.domain.conversation.controller;

import java.security.interfaces.RSAPublicKey;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.A501.backend.domain.conversation.dto.response.RealtimeJwkSetResponse;
import com.A501.backend.domain.conversation.service.RealtimeJwkConverter;
import com.A501.backend.domain.conversation.service.RealtimeTicketProvider;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * FastAPI가 Realtime Ticket 서명을 검증할 Public Key를 가져가는 엔드포인트. Public Key는
 * 비밀이 아니므로 인증을 요구하지 않는다.
 */
@Tag(name = "Realtime JWKS", description = "Realtime Ticket 서명 검증용 Public Key 노출")
@RestController
@RequestMapping("/internal/v1/realtime")
@RequiredArgsConstructor
public class RealtimeJwksController {

    private final RealtimeTicketProvider realtimeTicketProvider;

    @Operation(summary = "Realtime Ticket 검증용 JWK Set 조회")
    @GetMapping("/jwks")
    public ResponseEntity<RealtimeJwkSetResponse> getJwks() {
        RSAPublicKey publicKey = (RSAPublicKey) realtimeTicketProvider.publicKey();
        RealtimeJwkSetResponse response = new RealtimeJwkSetResponse(
                List.of(RealtimeJwkConverter.toJwk(publicKey, realtimeTicketProvider.kid())));
        return ResponseEntity.ok(response);
    }
}
