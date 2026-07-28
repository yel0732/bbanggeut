package com.A501.backend.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

// 키 접두사가 곧 소속 버킷을 결정한다. 아이 실물 사진이 공개 버킷으로 새지 않는 것이 이 규칙의
// 목적이므로, 버킷 선택 자체를 테스트로 고정한다.
class StoragePropertiesTest {

    private static final String PRESET_BUCKET = "a501-profile-presets";
    private static final String UPLOAD_BUCKET = "a501-profile-uploads";

    private static final StorageProperties PROPERTIES = new StorageProperties(
            PRESET_BUCKET,
            UPLOAD_BUCKET,
            "ap-northeast-2",
            "profile-presets/",
            "profile-uploads/",
            "https://cdn.example.com",
            "profile-presets/01.png",
            Set.of("profile-presets/01.png"),
            DataSize.ofMegabytes(2),
            Duration.ofMinutes(5));

    @Test
    void bucketFor_routesPresetKeyToPresetBucket() {
        assertThat(PROPERTIES.bucketFor("profile-presets/01.png")).isEqualTo(PRESET_BUCKET);
    }

    @Test
    void bucketFor_routesUploadKeyToUploadBucket() {
        assertThat(PROPERTIES.bucketFor("profile-uploads/1/parent/abc")).isEqualTo(UPLOAD_BUCKET);
        assertThat(PROPERTIES.bucketFor("profile-uploads/1/7/abc")).isEqualTo(UPLOAD_BUCKET);
    }

    // 접두사가 없는 키를 임의로 한쪽 버킷에 매핑하면 아이 사진이 공개 버킷에 쓰일 수 있다.
    @Test
    void bucketFor_failsForUnknownPrefix() {
        assertThatThrownBy(() -> PROPERTIES.bucketFor("something-else/abc"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> PROPERTIES.bucketFor(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void isPresetAndIsUpload_areMutuallyExclusive() {
        assertThat(PROPERTIES.isPreset("profile-presets/01.png")).isTrue();
        assertThat(PROPERTIES.isUpload("profile-presets/01.png")).isFalse();
        assertThat(PROPERTIES.isUpload("profile-uploads/1/parent/abc")).isTrue();
        assertThat(PROPERTIES.isPreset("profile-uploads/1/parent/abc")).isFalse();
    }
}
