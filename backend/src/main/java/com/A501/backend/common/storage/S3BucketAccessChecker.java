package com.A501.backend.common.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

// 자격증명·권한·버킷명·리전이 잘못된 경우는 코드로 검증할 수 없고, 그대로 두면 "배포는 성공했는데
// 사용자가 사진을 올리는 순간" 처음 드러난다. 기동 직후 한 번 확인해 배포 시점에 로그로 알린다.
//
// 실패해도 기동을 막지 않는다 — AWS 일시 장애나 네트워크 문제로 서버 자체가 뜨지 못하는 편이
// 더 위험하다. 잘못된 설정을 막는 역할은 S3StorageConfig의 기동 검증이 맡는다.
@Slf4j
@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "s3")
@RequiredArgsConstructor
public class S3BucketAccessChecker {

    private final S3Client s3Client;
    private final StorageProperties storageProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void checkBucketsOnStartup() {
        checkBucket(storageProperties.uploadBucket());
    }

    private void checkBucket(String bucket) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            log.info("S3 업로드 버킷 접근 확인: {}", bucket);
        } catch (RuntimeException e) {
            log.error("S3 업로드 버킷에 접근할 수 없습니다: {} — 자격증명·IAM 권한·버킷명·리전을 확인하세요."
                    + " 웹캠 사진 업로드가 동작하지 않습니다.", bucket, e);
        }
    }
}
