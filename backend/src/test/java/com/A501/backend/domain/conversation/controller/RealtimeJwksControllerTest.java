package com.A501.backend.domain.conversation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.A501.backend.domain.conversation.dto.response.RealtimeJwk;
import com.A501.backend.domain.conversation.dto.response.RealtimeJwkSetResponse;
import com.A501.backend.domain.conversation.service.RealtimeTicketIssueResult;
import com.A501.backend.domain.conversation.service.RealtimeTicketProvider;

import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Claims;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class RealtimeJwksControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RealtimeTicketProvider realtimeTicketProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void JWK_Set을_반환한다() throws Exception {
        mockMvc.perform(get("/internal/v1/realtime/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys.length()").value(1))
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].use").value("sig"))
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andExpect(jsonPath("$.keys[0].kid").value(realtimeTicketProvider.kid()))
                .andExpect(jsonPath("$.keys[0].n").isNotEmpty())
                .andExpect(jsonPath("$.keys[0].e").isNotEmpty());
    }

    @Test
    void 반환된_JWK로_실제_발급된_Ticket을_검증할_수_있다() throws Exception {
        RealtimeTicketIssueResult ticket = realtimeTicketProvider.issue(
                "conv_jwks_test", "rt_jwks_test", 1);

        String json = mockMvc.perform(get("/internal/v1/realtime/jwks"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        RealtimeJwkSetResponse jwkSet = objectMapper.readValue(json, RealtimeJwkSetResponse.class);
        RealtimeJwk jwk = jwkSet.keys().get(0);

        BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.n()));
        BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.e()));
        RSAPublicKey reconstructed = (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(modulus, exponent));

        Jws<Claims> parsed = Jwts.parser()
                .verifyWith(reconstructed)
                .build()
                .parseSignedClaims(ticket.ticket());
        assertThat(parsed.getPayload().get("conversationId", String.class))
                .isEqualTo("conv_jwks_test");
        assertThat(parsed.getHeader().get("kid")).isEqualTo(jwk.kid());
    }
}
