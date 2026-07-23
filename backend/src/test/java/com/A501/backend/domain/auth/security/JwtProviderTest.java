package com.A501.backend.domain.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.A501.backend.domain.auth.entity.Parent;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

class JwtProviderTest {

    private static final String SECRET = "test-secret-key-must-be-long-enough-for-hs256";

    private final JwtProperties jwtProperties = new JwtProperties(SECRET, 30, 10, 10);
    private final JwtProvider jwtProvider = new JwtProvider(jwtProperties);

    @Test
    void generateAccessToken_includesExpectedClaims() {
        Parent parent = createParentWithId(42L);

        String token = jwtProvider.generateAccessToken(parent);
        Claims claims = parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("email", String.class)).isEqualTo(parent.getEmail());
        assertThat(claims.get("purpose", String.class)).isEqualTo("ACCESS");
    }

    @Test
    void generateAccessToken_expiresAfterConfiguredMinutes() {
        Parent parent = createParentWithId(1L);

        String token = jwtProvider.generateAccessToken(parent);
        Claims claims = parseClaims(token);

        long secondsUntilExpiry = ChronoUnit.SECONDS.between(
                claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant());
        assertThat(secondsUntilExpiry).isEqualTo(30 * 60);
    }

    @Test
    void getAccessTokenExpirySeconds_convertsMinutesToSeconds() {
        assertThat(jwtProvider.getAccessTokenExpirySeconds()).isEqualTo(30 * 60L);
    }

    @Test
    void generateAccessToken_signatureIsRejectedWithWrongKey() {
        Parent parent = createParentWithId(1L);
        String token = jwtProvider.generateAccessToken(parent);
        SecretKey wrongKey = Keys.hmacShaKeyFor("a-completely-different-secret-key-for-this-test".getBytes());

        assertThatThrownBy(() -> Jwts.parser().verifyWith(wrongKey).build().parseSignedClaims(token))
                .isInstanceOf(SignatureException.class);
    }

    private Claims parseClaims(String token) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    private Parent createParentWithId(long parentId) {
        Parent parent = Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(parent, "parentId", parentId);
        return parent;
    }
}
