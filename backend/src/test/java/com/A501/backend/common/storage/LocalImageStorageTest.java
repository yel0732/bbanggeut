package com.A501.backend.common.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

// 가짜 저장소가 돌려주는 URL은 로컬에서 눈으로 확인하는 값이다. 프리셋 공개 URL이 비어 있을 때
// "/profile-presets/..." 같은 깨진 URL을 만들지 않는지 고정한다.
class LocalImageStorageTest {

    private static final String PRESET_KEY = "profile-presets/preset-1";
    private static final String UPLOAD_KEY = "profile-uploads/1/parent/abc";

    @Test
    void resolveUrl_marksUploadedImageAsSignedAndFake() {
        LocalImageStorage storage = new LocalImageStorage(properties(""));

        assertThat(storage.resolveUrl(UPLOAD_KEY)).isEqualTo("local://uploads/" + UPLOAD_KEY + "?signed=true");
    }

    @Test
    void resolveUrl_fallsBackToFakeUrlWhenPresetBaseUrlNotConfigured() {
        LocalImageStorage storage = new LocalImageStorage(properties(""));

        assertThat(storage.resolveUrl(PRESET_KEY)).isEqualTo("local://presets/" + PRESET_KEY);
    }

    // 개발자가 프리셋 공개 URL을 지정했다면 운영과 같은 형태를 보여준다.
    @Test
    void resolveUrl_usesConfiguredPresetBaseUrl() {
        LocalImageStorage storage = new LocalImageStorage(properties("https://cdn.example.com"));

        assertThat(storage.resolveUrl(PRESET_KEY)).isEqualTo("https://cdn.example.com/" + PRESET_KEY);
    }

    @Test
    void upload_returnsKeyUnderGivenPrefix() {
        LocalImageStorage storage = new LocalImageStorage(properties(""));

        String key = storage.upload("profile-uploads/1/parent/", new byte[] {1, 2, 3}, "image/jpeg");

        assertThat(key).startsWith("profile-uploads/1/parent/");
    }

    private StorageProperties properties(String presetPublicBaseUrl) {
        return new StorageProperties(
                "preset-bucket",
                "upload-bucket",
                "ap-northeast-2",
                "profile-presets/",
                "profile-uploads/",
                presetPublicBaseUrl,
                PRESET_KEY,
                Set.of(PRESET_KEY),
                DataSize.ofMegabytes(2),
                Duration.ofMinutes(5));
    }
}
