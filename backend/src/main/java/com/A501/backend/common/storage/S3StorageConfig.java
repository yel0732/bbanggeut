package com.A501.backend.common.storage;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

// S3 클라이언트 빈은 local 프로필에서 만들지 않는다 — 로컬 실행·테스트는 LocalImageStorage를 쓰므로
// AWS 자격증명이 없어도 애플리케이션이 떠야 한다.
//
// 프리셋·업로드 두 버킷은 같은 리전에 두므로 클라이언트는 하나로 공유한다. 버킷 선택은 요청마다
// StorageProperties.bucketFor(key)가 결정한다.
//
// 자격증명은 코드로 받지 않고 DefaultCredentialsProvider(환경변수·IAM 역할 등)에 맡긴다.
// 키를 설정 파일이나 코드에 두지 않기 위함이다.
@Configuration
@Profile("!local")
public class S3StorageConfig {

    @Bean
    public S3Client s3Client(StorageProperties storageProperties) {
        return S3Client.builder()
                .region(Region.of(storageProperties.region()))
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(StorageProperties storageProperties) {
        return S3Presigner.builder()
                .region(Region.of(storageProperties.region()))
                .build();
    }
}
