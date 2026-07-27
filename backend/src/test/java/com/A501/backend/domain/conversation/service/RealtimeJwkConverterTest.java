package com.A501.backend.domain.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.A501.backend.domain.conversation.dto.response.RealtimeJwk;

class RealtimeJwkConverterTest {

    @Test
    void RSA_Public_Key를_JWK로_변환한다() {
        RSAPublicKey publicKey = generateRsaPublicKey();

        RealtimeJwk jwk = RealtimeJwkConverter.toJwk(publicKey, "kid-1");

        assertThat(jwk.kty()).isEqualTo("RSA");
        assertThat(jwk.use()).isEqualTo("sig");
        assertThat(jwk.alg()).isEqualTo("RS256");
        assertThat(jwk.kid()).isEqualTo("kid-1");
        assertThat(jwk.n()).doesNotContain("+", "/", "=");
        assertThat(jwk.e()).doesNotContain("+", "/", "=");
    }

    @Test
    void JWK을_다시_RSA_Public_Key로_복원하면_원본과_같다() throws Exception {
        RSAPublicKey publicKey = generateRsaPublicKey();
        RealtimeJwk jwk = RealtimeJwkConverter.toJwk(publicKey, "kid-1");

        BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.n()));
        BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.e()));
        RSAPublicKey reconstructed = (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(modulus, exponent));

        assertThat(reconstructed.getModulus()).isEqualTo(publicKey.getModulus());
        assertThat(reconstructed.getPublicExponent()).isEqualTo(publicKey.getPublicExponent());
    }

    private RSAPublicKey generateRsaPublicKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            return (RSAPublicKey) keyPair.getPublic();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
