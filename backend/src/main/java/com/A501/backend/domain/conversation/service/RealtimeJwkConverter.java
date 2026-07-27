package com.A501.backend.domain.conversation.service;

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Base64;

import com.A501.backend.domain.conversation.dto.response.RealtimeJwk;

/** RSA Public Key를 표준 JWK(RFC 7517) 형태로 변환한다. */
public final class RealtimeJwkConverter {

    private static final String KEY_TYPE_RSA = "RSA";
    private static final String USE_SIGNATURE = "sig";
    private static final String ALGORITHM_RS256 = "RS256";

    private RealtimeJwkConverter() {
    }

    public static RealtimeJwk toJwk(RSAPublicKey publicKey, String kid) {
        return new RealtimeJwk(
                KEY_TYPE_RSA,
                USE_SIGNATURE,
                ALGORITHM_RS256,
                kid,
                encodeUnsigned(publicKey.getModulus()),
                encodeUnsigned(publicKey.getPublicExponent()));
    }

    private static String encodeUnsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
