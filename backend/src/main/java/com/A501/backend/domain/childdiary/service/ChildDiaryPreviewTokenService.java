package com.A501.backend.domain.childdiary.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.childdiary.exception.ChildDiaryErrorCode;
import com.A501.backend.domain.childdiary.model.ChildDiaryDraft;
import com.A501.backend.domain.childdiary.model.ChildDiaryPreviewProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class ChildDiaryPreviewTokenService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final ChildDiaryPreviewProperties properties;
    private final Clock clock;

    @Autowired
    public ChildDiaryPreviewTokenService(ObjectMapper objectMapper,
            ChildDiaryPreviewProperties properties) {
        this(objectMapper, properties, Clock.systemUTC());
    }

    ChildDiaryPreviewTokenService(ObjectMapper objectMapper,
            ChildDiaryPreviewProperties properties, Clock clock) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.secret = properties.secret().getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    public long expirationEpochSecond() {
        return clock.instant().plus(properties.ttl()).getEpochSecond();
    }

    public String issue(ChildDiaryDraft draft) {
        try {
            String payload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(draft));
            return payload + "." + sign(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize a child diary preview.", exception);
        }
    }

    public ChildDiaryDraft verify(String token) {
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.", -1);
            if (parts.length != 2 || !MessageDigest.isEqual(
                    sign(parts[0]).getBytes(StandardCharsets.US_ASCII),
                    parts[1].getBytes(StandardCharsets.US_ASCII))) {
                throw invalid();
            }
            ChildDiaryDraft draft = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(parts[0]), ChildDiaryDraft.class);
            if (draft.expiresAtEpochSecond() < clock.instant().getEpochSecond()) throw invalid();
            return draft;
        } catch (IllegalArgumentException | JacksonException exception) {
            throw invalid();
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not sign a child diary preview.", exception);
        }
    }

    private BusinessException invalid() {
        return new BusinessException(ChildDiaryErrorCode.PREVIEW_INVALID);
    }
}
