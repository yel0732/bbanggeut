package com.A501.backend.common.storage;

import java.time.Duration;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

// 프리셋/업로드 구분 규칙(isPreset/isUpload/bucketFor)을 여기 한 곳에 둔다 — 저장소 구현과
// ProfileImageService가 같은 판정을 필요로 하므로, 각자 접두사를 비교하면 규칙이 흩어진다.
//
// **버킷을 둘로 나누는 이유**: S3의 Block Public Access는 접두사가 아니라 버킷 단위 설정이다.
// 한 버킷에서 프리셋만 공개하려면 그 버킷의 공개 차단을 풀어야 하는데, 그 버킷에는 아이 실물
// 사진도 함께 들어 있다. 버킷 정책에서 접두사를 빠뜨리는 실수 한 번이면 아이 사진이 공개되고,
// 그 실수를 막아줄 안전망(BPA)은 이미 꺼둔 상태가 된다. 업로드 버킷을 분리하면 BPA를 전부 켠 채
// 둘 수 있어 잘못된 공개 정책 자체가 AWS에서 거부된다.
//
// DB에는 키만 저장되므로 키 접두사가 곧 소속 버킷을 결정한다(bucketFor).
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        // 기본 제공 프리셋 전용 버킷. 모두가 공유하는 이미지라 공개 읽기를 허용한다.
        String presetBucket,
        // 사용자 업로드(웹캠 촬영) 전용 버킷. 아이 실물 사진이므로 공개 차단을 전부 켜고
        // presigned GET으로만 접근한다.
        String uploadBucket,
        String region,
        String presetPrefix,
        String uploadPrefix,
        // 프리셋 버킷(또는 그 앞단 CDN)의 공개 베이스 URL. 업로드 버킷에는 공개 URL이 없다.
        String presetPublicBaseUrl,
        String defaultPresetKey,
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

    // 어느 접두사에도 속하지 않는 키는 저장소에 닿아선 안 된다. 임의로 버킷을 고르면 아이 사진이
    // 공개 버킷에 쓰일 수 있으므로, 조용히 넘어가지 않고 실패시킨다.
    public String bucketFor(String key) {
        if (isUpload(key)) {
            return uploadBucket;
        }
        if (isPreset(key)) {
            return presetBucket;
        }
        throw new IllegalStateException("알 수 없는 이미지 키 접두사입니다: " + key);
    }
}
