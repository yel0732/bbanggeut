package com.A501.backend.common.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// 기본 프리셋 키는 신규 계정·자녀에 자동으로 붙고, 업로드 사진을 파기한 뒤 되돌아갈 자리이기도 하다.
// 그 키가 허용 목록에 없으면 사용자가 그 이미지를 "다시 선택"하려는 순간 400으로 거절된다 —
// 설정 두 값이 서로 어긋나는 것이라 코드로는 드러나지 않으므로 테스트로 고정한다.
@SpringBootTest
class StorageDefaultPresetTest {

    @Autowired
    private StorageProperties storageProperties;

    @Test
    void defaultPresetKeyIsOneOfAllowedPresetKeys() {
        assertThat(storageProperties.presetKeys()).contains(storageProperties.defaultPresetKey());
    }

    @Test
    void defaultPresetKeyLivesUnderPresetPrefix() {
        assertThat(storageProperties.isPreset(storageProperties.defaultPresetKey())).isTrue();
    }
}
