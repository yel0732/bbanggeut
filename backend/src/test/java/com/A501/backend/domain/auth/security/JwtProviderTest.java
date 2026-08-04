package com.A501.backend.domain.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.auth.entity.Parent;
import com.A501.backend.domain.auth.entity.ParentRole;
import com.A501.backend.domain.auth.exception.AuthErrorCode;

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
        assertThat(claims.get("role", String.class)).isEqualTo("PARENT");
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

    @Test
    void parseAccessToken_returnsParentIdForValidToken() {
        Parent parent = createParentWithId(42L);
        String token = jwtProvider.generateAccessToken(parent);

        assertThat(jwtProvider.parseAccessToken(token)).isEqualTo(42L);
    }

    @Test
    void parseAccessTokenClaims_returnsAdminRole() {
        Parent parent = createParentWithId(42L);
        ReflectionTestUtils.setField(parent, "role", ParentRole.ADMIN);

        JwtProvider.AccessTokenClaims claims = jwtProvider.parseAccessTokenClaims(
                jwtProvider.generateAccessToken(parent));

        assertThat(claims.parentId()).isEqualTo(42L);
        assertThat(claims.role()).isEqualTo(ParentRole.ADMIN);
    }

    @Test
    void parseAccessToken_returnsNullForTamperedToken() {
        Parent parent = createParentWithId(42L);
        String token = jwtProvider.generateAccessToken(parent);
        String tamperedSignature = tamperSignature(token);

        assertThat(jwtProvider.parseAccessToken(tamperedSignature)).isNull();
    }

    @Test
    void parseAccessToken_returnsNullForGarbageInput() {
        assertThat(jwtProvider.parseAccessToken("not-a-jwt")).isNull();
    }

    @Test
    void parseAccessToken_returnsNullForExpiredToken() {
        JwtProvider expiredTokenProvider = new JwtProvider(new JwtProperties(SECRET, -1, 10, 10));
        Parent parent = createParentWithId(42L);
        String expiredToken = expiredTokenProvider.generateAccessToken(parent);

        assertThat(jwtProvider.parseAccessToken(expiredToken)).isNull();
    }

    @Test
    void generateSignupToken_roundTripsProviderIdAndEmail() {
        String token = jwtProvider.generateSignupToken("google-sub-123", "new@example.com");

        JwtProvider.SignupTokenClaims claims = jwtProvider.parseSignupToken(token);

        assertThat(claims.providerId()).isEqualTo("google-sub-123");
        assertThat(claims.email()).isEqualTo("new@example.com");
    }

    @Test
    void generateLinkToken_roundTripsParentIdAndProviderId() {
        String token = jwtProvider.generateLinkToken(7L, "google-sub-456");

        JwtProvider.LinkTokenClaims claims = jwtProvider.parseLinkToken(token);

        assertThat(claims.parentId()).isEqualTo(7L);
        assertThat(claims.providerId()).isEqualTo("google-sub-456");
    }

    @Test
    void parseSignupToken_throwsWhenGivenAccessToken() {
        Parent parent = createParentWithId(1L);
        String accessToken = jwtProvider.generateAccessToken(parent);

        assertThatThrownBy(() -> jwtProvider.parseSignupToken(accessToken))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_SIGNUP_TOKEN));
    }

    @Test
    void parseLinkToken_throwsWhenGivenSignupToken() {
        String signupToken = jwtProvider.generateSignupToken("google-sub", "a@example.com");

        assertThatThrownBy(() -> jwtProvider.parseLinkToken(signupToken))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_LINK_TOKEN));
    }

    @Test
    void parseSignupToken_throwsForTamperedToken() {
        String token = jwtProvider.generateSignupToken("google-sub", "a@example.com");
        String tampered = tamperSignature(token);

        assertThatThrownBy(() -> jwtProvider.parseSignupToken(tampered))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_SIGNUP_TOKEN));
    }

    @Test
    void parseSignupToken_throwsForExpiredToken() {
        JwtProvider expiredTokenProvider = new JwtProvider(new JwtProperties(SECRET, 30, -1, 10));
        String expiredToken = expiredTokenProvider.generateSignupToken("google-sub", "a@example.com");

        assertThatThrownBy(() -> jwtProvider.parseSignupToken(expiredToken))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_SIGNUP_TOKEN));
    }

    private String tamperSignature(String token) {
        int signatureStart = token.lastIndexOf('.') + 1;
        char original = token.charAt(signatureStart);
        char replacement = original == 'A' ? 'B' : 'A';
        return token.substring(0, signatureStart)
                + replacement
                + token.substring(signatureStart + 1);
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
