package com.A501.backend.common.storage;

import java.time.Duration;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

// 프리셋/업로드 구분 규칙(isPreset/isUpload)을 여기 한 곳에 둔다 — 저장소 구현과
// ProfileImageService가 같은 판정을 필요로 하므로, 각자 접두사를 비교하면 규칙이 흩어진다.
//
// **프리셋은 S3에 없다.** 프론트엔드 번들에 포함된 이미지이고, 키는 그 이미지를 가리키는 공유
// 식별자일 뿐이다. 따라서 접두사는 "어느 버킷이냐"가 아니라 **"프론트 번들 식별자냐 S3 객체 키냐"**
// 를 구분한다. S3 버킷은 업로드(아이 실물 사진)용 하나뿐이며, 공개 버킷은 아예 두지 않는다
// — 공개 버킷이 없으면 공개 설정 실수 자체가 불가능하다.
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        // 사용자 업로드(웹캠 촬영) 전용 버킷. 퍼블릭 액세스 차단을 전부 켠 채 두고
        // presigned GET으로만 접근한다.
        String uploadBucket,
        String region,
        // 프론트 번들 프리셋 이미지의 식별자 접두사 (S3 경로가 아니다)
        String presetPrefix,
        String uploadPrefix,
        String defaultParentPresetKey,
        String defaultChildPresetKey,
        // 클라이언트가 지정할 수 있는 프리셋 키의 화이트리스트. 정확히 일치하는 키만 허용해
        // 경로 조작(../ 등)과 임의 키 지정을 원천 차단한다.
        Set<String> presetKeys,
        DataSize maxImageSize,
        Duration presignedUrlTtl
) {

    public boolean isPreset(String key) {
        return key != null && key.startsWith(presetPrefix);
    }

    public boolean isUpload(String key) {
        return key != null && key.startsWith(uploadPrefix);
    }
}
