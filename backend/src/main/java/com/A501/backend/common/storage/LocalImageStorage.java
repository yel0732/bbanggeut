package com.A501.backend.common.storage;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// AWS 인프라 없이 로컬 실행·테스트가 가능하도록 하는 가짜 구현
// (domain/conversation의 LocalChildAccessVerifier와 같은 @Profile("local") 패턴).
//
// 실제 파일을 디스크에 쓰지 않고 메모리에만 보관한다. 아이 실물 사진이 개발자 PC에 남지 않게
// 하려는 의도이며, 애플리케이션을 내리면 함께 사라진다.
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalImageStorage implements ImageStorage {

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    private final StorageProperties storageProperties;

    @Override
    public String upload(String keyPrefix, byte[] content, String contentType) {
        String key = keyPrefix + UUID.randomUUID();
        objects.put(key, content);
        log.info("[local] 이미지 업로드: key={}, contentType={}, size={}B", key, contentType, content.length);
        return key;
    }

    @Override
    public String resolveUrl(String key) {
        if (!storageProperties.isUpload(key)) {
            return storageProperties.presetPublicBaseUrl() + "/" + key;
        }
        // 업로드 이미지는 실제 환경에서 비공개 버킷의 presigned GET URL로만 나간다. 로컬에는 대응하는
        // 실제 객체가 없으므로, 열리는 척하는 http URL 대신 가짜임이 드러나는 형태를 돌려준다.
        return "local://" + storageProperties.uploadBucket() + "/" + key + "?signed=true";
    }

    @Override
    public void delete(String key) {
        objects.remove(key);
        log.info("[local] 이미지 삭제: key={}", key);
    }
}
