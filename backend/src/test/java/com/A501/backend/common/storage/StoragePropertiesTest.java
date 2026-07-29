package com.A501.backend.common.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

// 접두사가 "프론트 번들 프리셋 식별자"와 "S3 객체 키"를 가른다. 이 판정이 흔들리면 프리셋을
// S3에서 지우려 하거나(존재하지 않는 객체), 아이 실물 사진을 프리셋으로 오인해 파기하지 않게 된다.
class StoragePropertiesTest {

    private static final StorageProperties PROPERTIES = new StorageProperties(
            "a501-profile-uploads",
            "ap-northeast-2",
            "profile-presets/",
            "profile-uploads/",
            "profile-presets/parent-avatar",
            "profile-presets/child-avatar",
            Set.of("profile-presets/parent-avatar", "profile-presets/child-avatar"),
            DataSize.ofMegabytes(2),
            Duration.ofMinutes(5));

    @Test
    void isPresetAndIsUpload_areMutuallyExclusive() {
        assertThat(PROPERTIES.isPreset("profile-presets/child-avatar-tiger")).isTrue();
        assertThat(PROPERTIES.isUpload("profile-presets/child-avatar-tiger")).isFalse();

        assertThat(PROPERTIES.isUpload("profile-uploads/1/parent/abc")).isTrue();
        assertThat(PROPERTIES.isPreset("profile-uploads/1/parent/abc")).isFalse();
    }

    @Test
    void unknownPrefixIsNeitherPresetNorUpload() {
        assertThat(PROPERTIES.isPreset("something-else/abc")).isFalse();
        assertThat(PROPERTIES.isUpload("something-else/abc")).isFalse();
        assertThat(PROPERTIES.isPreset(null)).isFalse();
        assertThat(PROPERTIES.isUpload(null)).isFalse();
    }

    // 부모와 자녀의 기본 이미지는 서로 다르다.
    @Test
    void parentAndChildDefaultsDiffer() {
        assertThat(PROPERTIES.defaultParentPresetKey()).isNotEqualTo(PROPERTIES.defaultChildPresetKey());
    }
}
