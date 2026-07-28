package com.A501.backend.common.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

// S3 클라이언트는 STORAGE_PROVIDER=s3 일 때만 만든다 — 로컬 실행·테스트는 LocalImageStorage를
// 쓰므로 AWS 자격증명이 없어도 애플리케이션이 떠야 한다.
//
// 자격증명은 코드로 받지 않고 AWS 기본 자격증명 체인(AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY
// 환경변수, EC2 IAM 역할 등)에 맡긴다. 키가 코드·설정 파일·이미지에 남지 않게 하기 위함이다.
//
// 프리셋·업로드 두 버킷은 같은 리전에 두므로 클라이언트는 하나로 공유한다. 버킷 선택은 요청마다
// StorageProperties.bucketFor(key)가 결정한다.
@Configuration
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "s3")
@RequiredArgsConstructor
public class S3StorageConfig {

    private final StorageProperties storageProperties;

    // 버킷 이름이 비어 있으면 기동을 막는다. 기본값을 두면 우리가 소유하지도 않은 버킷 이름으로
    // 조용히 뜬 뒤, 아이 사진을 올리는 시점에야 실패한다 — 그 전에 드러나야 한다.
    @PostConstruct
    void validateRequiredSettings() {
        requireConfigured("S3_PRESET_BUCKET", storageProperties.presetBucket());
        requireConfigured("S3_UPLOAD_BUCKET", storageProperties.uploadBucket());
        requireConfigured("S3_PRESET_PUBLIC_BASE_URL", storageProperties.presetPublicBaseUrl());
        requireConfigured("AWS_REGION", storageProperties.region());
    }

    private void requireConfigured(String environmentVariable, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "STORAGE_PROVIDER=s3 로 실행하려면 " + environmentVariable + " 환경변수가 필요합니다.");
        }
    }

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(storageProperties.region()))
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(storageProperties.region()))
                .build();
    }
}
