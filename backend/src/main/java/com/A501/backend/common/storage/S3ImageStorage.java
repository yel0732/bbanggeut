package com.A501.backend.common.storage;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.A501.backend.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

// 업로드(아이 실물 사진) 전용. 버킷은 하나이며 퍼블릭 액세스가 전부 차단되어 있어,
// 접근은 소유자에게 발급하는 presigned GET URL로만 이뤄진다.
@Slf4j
@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "s3")
@RequiredArgsConstructor
public class S3ImageStorage implements ImageStorage {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties storageProperties;

    @Override
    public String upload(String keyPrefix, byte[] content, String contentType) {
        String key = keyPrefix + UUID.randomUUID();
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(storageProperties.uploadBucket())
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(content));
        } catch (RuntimeException e) {
            // 실패 원인(버킷명·권한 등)이 사용자 응답에 새어나가지 않도록 로그로만 남긴다.
            log.error("S3 이미지 업로드 실패: key={}", key, e);
            throw new BusinessException(ImageErrorCode.IMAGE_UPLOAD_FAILED);
        }
        return key;
    }

    @Override
    public String resolveUploadUrl(String key) {
        // s3Presigner는 스프링이 관리하는 싱글턴이라 여기서 close하지 않는다.
        return s3Presigner.presignGetObject(
                        GetObjectPresignRequest.builder()
                                .signatureDuration(storageProperties.presignedUrlTtl())
                                .getObjectRequest(GetObjectRequest.builder()
                                        .bucket(storageProperties.uploadBucket())
                                        .key(key)
                                        .build())
                                .build())
                .url()
                .toString();
    }

    @Override
    public void delete(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(storageProperties.uploadBucket())
                    .key(key)
                    .build());
        } catch (RuntimeException e) {
            // 삭제 실패로 프로필 변경·자녀 삭제·탈퇴 자체가 실패하면 안 된다. 남은 객체는 S3 lifecycle이
            // 정리하도록 두고, 여기서는 추적 가능하도록 로그만 남긴다.
            log.error("S3 이미지 삭제 실패: key={}", key, e);
        }
    }
}
