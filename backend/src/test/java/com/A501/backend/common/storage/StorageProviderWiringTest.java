package com.A501.backend.common.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

// STORAGE_PROVIDER 스위치와 기동 검증은 "실제 S3로 바꾸는 순간"에만 작동하는 코드라, 테스트가 없으면
// 그 전환 시점에 처음 확인하게 된다. 전체 애플리케이션을 띄우지 않는 ApplicationContextRunner를 쓰는
// 이유는 ApplicationReadyEvent가 발행되지 않아 S3BucketAccessChecker가 실제 AWS를 호출하지 않기 때문이다.
class StorageProviderWiringTest {

    private static final String[] BUCKET_SETTINGS = {
            "app.storage.upload-bucket=test-upload-bucket",
            "app.storage.region=ap-northeast-2"
    };

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    StorageTestConfig.class, S3StorageConfig.class, S3ImageStorage.class, LocalImageStorage.class)
            .withPropertyValues(
                    "app.storage.preset-prefix=profile-presets/",
                    "app.storage.upload-prefix=profile-uploads/",
                    "app.storage.default-parent-preset-key=profile-presets/parent-avatar",
                    "app.storage.default-child-preset-key=profile-presets/child-avatar",
                    "app.storage.preset-keys[0]=profile-presets/parent-avatar",
                    "app.storage.preset-keys[1]=profile-presets/child-avatar",
                    "app.storage.max-image-size=2MB",
                    "app.storage.presigned-url-ttl=5m");

    // STORAGE_PROVIDER를 지정하지 않으면 AWS 없이 뜨는 가짜 저장소여야 한다.
    @Test
    void usesLocalStorageByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(LocalImageStorage.class);
            assertThat(context).doesNotHaveBean(S3ImageStorage.class);
        });
    }

    @Test
    void usesS3StorageWhenProviderIsS3() {
        contextRunner
                .withPropertyValues("app.storage.provider=s3")
                .withPropertyValues(BUCKET_SETTINGS)
                .run(context -> {
                    assertThat(context).hasSingleBean(S3ImageStorage.class);
                    assertThat(context).doesNotHaveBean(LocalImageStorage.class);
                });
    }

    // 버킷을 지정하지 않은 채 s3로 뜨면, 소유하지도 않은 버킷 이름으로 동작하다 아이 사진 업로드
    // 시점에야 실패한다. 그 전에 기동을 막는지 확인한다.
    @Test
    void failsToStartWhenS3SelectedWithoutBucketSettings() {
        contextRunner
                .withPropertyValues("app.storage.provider=s3")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .hasMessageContaining("S3_UPLOAD_BUCKET"));
    }

    @Test
    void failsToStartWhenRegionMissing() {
        contextRunner
                .withPropertyValues("app.storage.provider=s3")
                .withPropertyValues("app.storage.upload-bucket=test-upload-bucket", "app.storage.region=")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .hasMessageContaining("AWS_REGION"));
    }

    @Configuration
    @EnableConfigurationProperties(StorageProperties.class)
    static class StorageTestConfig {
    }
}
