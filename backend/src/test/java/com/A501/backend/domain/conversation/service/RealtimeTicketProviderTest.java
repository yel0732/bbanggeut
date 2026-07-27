package com.A501.backend.domain.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Claims;

class RealtimeTicketProviderTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-27T01:00:00Z"), ZoneOffset.UTC);

    /**
     * jjwt 파서는 {@code exp} 검증에 기본적으로 실제 시스템 시각을 사용한다. 발급
     * 시각을 {@link #FIXED_CLOCK}으로 고정했으므로 검증도 같은 시각으로 고정해
     * 테스트 실행 시점(실제 시스템 시각)에 좌우되지 않게 한다.
     */
    private static final io.jsonwebtoken.Clock AT_ISSUANCE =
            () -> Date.from(FIXED_CLOCK.instant());

    @Test
    void PEM_미설정_시_자동_생성한_키로_서명하고_같은_Public_Key로_검증된다() {
        RealtimeTicketProperties properties = new RealtimeTicketProperties(
                "spring-backend", "ai-realtime", Duration.ofMinutes(5), null, null);
        RealtimeTicketProvider provider = new RealtimeTicketProvider(properties, FIXED_CLOCK);

        RealtimeTicketIssueResult result = provider.issue("conv_1", "rt_1", 1);

        Jws<Claims> parsed = Jwts.parser()
                .verifyWith(provider.publicKey())
                .clock(AT_ISSUANCE)
                .build()
                .parseSignedClaims(result.ticket());

        Claims claims = parsed.getPayload();
        assertThat(claims.getIssuer()).isEqualTo("spring-backend");
        assertThat(claims.getAudience()).containsExactly("ai-realtime");
        assertThat(claims.get("conversationId", String.class)).isEqualTo("conv_1");
        assertThat(claims.get("realtimeSessionId", String.class)).isEqualTo("rt_1");
        assertThat(claims.get("sessionEpoch", Integer.class)).isEqualTo(1);
        assertThat(claims.get("scope", List.class)).containsExactly("realtime:connect");
        assertThat(claims.getId()).isEqualTo(result.jti());
        assertThat(claims.getExpiration()).isEqualTo(
                Date.from(result.expiresAt().toInstant(ZoneOffset.UTC)));
        assertThat(parsed.getHeader().get("kid")).isEqualTo(provider.kid());
    }

    @Test
    void 같은_Public_Key면_kid가_항상_같다() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String privateKeyPem = toPem("PRIVATE KEY", keyPair.getPrivate().getEncoded());
        String publicKeyPem = toPem("PUBLIC KEY", keyPair.getPublic().getEncoded());
        RealtimeTicketProperties properties = new RealtimeTicketProperties(
                "spring-backend", "ai-realtime", Duration.ofMinutes(5), privateKeyPem, publicKeyPem);

        RealtimeTicketProvider first = new RealtimeTicketProvider(properties, FIXED_CLOCK);
        RealtimeTicketProvider second = new RealtimeTicketProvider(properties, FIXED_CLOCK);

        assertThat(first.kid()).isEqualTo(second.kid());
    }

    @Test
    void 자동_생성된_키페어끼리는_kid가_서로_다르다() {
        RealtimeTicketProperties properties = new RealtimeTicketProperties(
                "spring-backend", "ai-realtime", Duration.ofMinutes(5), null, null);

        RealtimeTicketProvider first = new RealtimeTicketProvider(properties, FIXED_CLOCK);
        RealtimeTicketProvider second = new RealtimeTicketProvider(properties, FIXED_CLOCK);

        assertThat(first.kid()).isNotEqualTo(second.kid());
    }

    @Test
    void 매번_다른_jti를_발급한다() {
        RealtimeTicketProperties properties = new RealtimeTicketProperties(
                "spring-backend", "ai-realtime", Duration.ofMinutes(5), null, null);
        RealtimeTicketProvider provider = new RealtimeTicketProvider(properties, FIXED_CLOCK);

        RealtimeTicketIssueResult first = provider.issue("conv_1", "rt_1", 1);
        RealtimeTicketIssueResult second = provider.issue("conv_1", "rt_1", 1);

        assertThat(first.jti()).isNotEqualTo(second.jti());
    }

    @Test
    void 설정된_PEM_키페어로_서명하면_그_외부_Public_Key로만_검증된다() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair externalKeyPair = generator.generateKeyPair();
        String privateKeyPem = toPem("PRIVATE KEY", externalKeyPair.getPrivate().getEncoded());
        String publicKeyPem = toPem("PUBLIC KEY", externalKeyPair.getPublic().getEncoded());

        RealtimeTicketProperties properties = new RealtimeTicketProperties(
                "spring-backend", "ai-realtime", Duration.ofMinutes(5), privateKeyPem, publicKeyPem);
        RealtimeTicketProvider provider = new RealtimeTicketProvider(properties, FIXED_CLOCK);

        RealtimeTicketIssueResult result = provider.issue("conv_1", "rt_1", 1);

        Jws<Claims> parsed = Jwts.parser()
                .verifyWith(externalKeyPair.getPublic())
                .clock(AT_ISSUANCE)
                .build()
                .parseSignedClaims(result.ticket());
        assertThat(parsed.getPayload().get("conversationId", String.class)).isEqualTo("conv_1");
        assertThat(provider.publicKey()).isEqualTo(externalKeyPair.getPublic());
        assertThat(parsed.getHeader().get("kid")).isEqualTo(provider.kid());
    }

    @Test
    void 만료된_Ticket은_검증에_실패한다() {
        RealtimeTicketProperties properties = new RealtimeTicketProperties(
                "spring-backend", "ai-realtime", Duration.ofMinutes(5), null, null);
        RealtimeTicketProvider provider = new RealtimeTicketProvider(properties, FIXED_CLOCK);
        RealtimeTicketIssueResult result = provider.issue("conv_1", "rt_1", 1);

        io.jsonwebtoken.Clock afterExpiry = () -> Date.from(
                Instant.parse("2026-07-27T01:10:00Z"));

        assertThatThrownBy(() -> Jwts.parser()
                .verifyWith(provider.publicKey())
                .clock(afterExpiry)
                .build()
                .parseSignedClaims(result.ticket()))
                .isInstanceOf(ExpiredJwtException.class);
    }

    private static String toPem(String label, byte[] encoded) {
        String base64 = Base64.getEncoder().encodeToString(encoded);
        return "-----BEGIN " + label + "-----\n" + base64 + "\n-----END " + label + "-----\n";
    }
}
