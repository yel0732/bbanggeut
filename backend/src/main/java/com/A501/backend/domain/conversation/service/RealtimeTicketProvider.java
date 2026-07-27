package com.A501.backend.domain.conversation.service;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Jwts;

/**
 * Realtime Ticket을 RS256으로 서명해 발급한다(ADR-003/004). Access Token은 절대 다루지
 * 않으며, Ticket 원문·Private Key는 어디에도 로그로 남기지 않는다.
 *
 * <p>Ticket 검증·일회용 소비는 FastAPI와 W02가 담당한다. 이 컴포넌트는 서명 키를
 * 보관하고 발급만 수행한다({@link #publicKey()}는 후속 W03 JWKS 노출 지점).
 */
@Component
public class RealtimeTicketProvider {

    private static final String KEY_ALGORITHM = "RSA";
    private static final int GENERATED_KEY_SIZE = 2048;
    private static final String TICKET_ID_PREFIX = "ticket_";
    private static final String SCOPE_REALTIME_CONNECT = "realtime:connect";
    private static final String CLAIM_CONVERSATION_ID = "conversationId";
    private static final String CLAIM_REALTIME_SESSION_ID = "realtimeSessionId";
    private static final String CLAIM_SESSION_EPOCH = "sessionEpoch";
    private static final String CLAIM_SCOPE = "scope";
    private static final String HEADER_KID = "kid";
    private static final String FINGERPRINT_ALGORITHM = "SHA-256";
    private static final int KID_LENGTH_BYTES = 8;

    private final RealtimeTicketProperties properties;
    private final Clock utcClock;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final String kid;

    public RealtimeTicketProvider(
            RealtimeTicketProperties properties,
            @Qualifier("utcClock") Clock utcClock) {
        this.properties = properties;
        this.utcClock = utcClock;
        KeyPair keyPair = resolveKeyPair(properties);
        this.privateKey = keyPair.getPrivate();
        this.publicKey = keyPair.getPublic();
        this.kid = computeKid(this.publicKey);
    }

    public RealtimeTicketIssueResult issue(
            String conversationId,
            String realtimeSessionId,
            int sessionEpoch) {
        String jti = TICKET_ID_PREFIX + UUID.randomUUID().toString().replace("-", "");
        LocalDateTime issuedAt = LocalDateTime.now(utcClock);
        LocalDateTime expiresAt = issuedAt.plus(properties.ttl());

        String ticket = Jwts.builder()
                .header().add(HEADER_KID, kid).and()
                .issuer(properties.issuer())
                .audience().add(properties.audience()).and()
                .claim(CLAIM_CONVERSATION_ID, conversationId)
                .claim(CLAIM_REALTIME_SESSION_ID, realtimeSessionId)
                .claim(CLAIM_SESSION_EPOCH, sessionEpoch)
                .claim(CLAIM_SCOPE, List.of(SCOPE_REALTIME_CONNECT))
                .id(jti)
                .issuedAt(toDate(issuedAt))
                .expiration(toDate(expiresAt))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();

        return new RealtimeTicketIssueResult(ticket, jti, expiresAt);
    }

    /** FastAPI가 JWKS로 가져갈 검증용 Public Key. */
    public PublicKey publicKey() {
        return publicKey;
    }

    /**
     * Public Key 지문 기반 키 식별자. 같은 키면 항상 같은 값이고, 키가 바뀌면(local
     * 재부팅으로 키페어가 재생성되는 경우 포함) 값도 바뀐다.
     */
    public String kid() {
        return kid;
    }

    private static Date toDate(LocalDateTime utc) {
        return Date.from(utc.toInstant(ZoneOffset.UTC));
    }

    private static String computeKid(PublicKey publicKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance(FINGERPRINT_ALGORITHM);
            byte[] hash = digest.digest(publicKey.getEncoded());
            return HexFormat.of().formatHex(hash, 0, KID_LENGTH_BYTES);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Public Key 지문을 계산할 수 없습니다.", exception);
        }
    }

    private static KeyPair resolveKeyPair(RealtimeTicketProperties properties) {
        if (properties.privateKeyPem() != null && properties.publicKeyPem() != null) {
            return parseKeyPair(properties.privateKeyPem(), properties.publicKeyPem());
        }
        return generateEphemeralKeyPair();
    }

    private static KeyPair generateEphemeralKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
            generator.initialize(GENERATED_KEY_SIZE);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Realtime Ticket용 RSA 키페어를 생성할 수 없습니다.", exception);
        }
    }

    private static KeyPair parseKeyPair(String privateKeyPem, String publicKeyPem) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
            PrivateKey parsedPrivateKey = keyFactory.generatePrivate(
                    new PKCS8EncodedKeySpec(decodePem(privateKeyPem)));
            PublicKey parsedPublicKey = keyFactory.generatePublic(
                    new X509EncodedKeySpec(decodePem(publicKeyPem)));
            return new KeyPair(parsedPublicKey, parsedPrivateKey);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Realtime Ticket 서명 키를 읽을 수 없습니다.", exception);
        }
    }

    private static byte[] decodePem(String pem) {
        String base64Only = pem
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64Only);
    }
}
