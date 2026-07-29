package com.A501.backend.common.storage;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// AWS 인프라 없이 로컬 실행·테스트가 가능하도록 하는 가짜 구현.
//
// 선택 기준을 Spring 프로필이 아니라 STORAGE_PROVIDER로 두는 이유: 프로필로 묶으면 "로컬 H2 +
// 진짜 S3" 조합이 불가능해진다(프로필을 prod로 바꾸는 순간 MySQL·JWT_SECRET 등이 전부 필요).
// 저장소 선택을 분리해야 개발자가 로컬 DB 그대로 개인 AWS 버킷에 붙여 검증할 수 있다.
//
// 실제 파일을 디스크에 쓰지 않고 메모리에만 보관한다. 아이 실물 사진이 개발자 PC에 남지 않게
// 하려는 의도이며, 애플리케이션을 내리면 함께 사라진다.
@Slf4j
@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local", matchIfMissing = true)
@RequiredArgsConstructor
public class LocalImageStorage implements ImageStorage {

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @Override
    public String upload(String keyPrefix, byte[] content, String contentType) {
        String key = keyPrefix + UUID.randomUUID();
        objects.put(key, content);
        log.info("[local] 이미지 업로드: key={}, contentType={}, size={}B", key, contentType, content.length);
        return key;
    }

    @Override
    public String resolveUploadUrl(String key) {
        // 로컬에는 대응하는 실제 객체가 없다. 열리는 척하는 http URL 대신 가짜임이 드러나는 형태를
        // 돌려줘, 연동 중인 프론트가 "왜 이미지가 안 뜨지?"로 시간을 쓰지 않게 한다.
        return "local://uploads/" + key + "?signed=true";
    }

    @Override
    public void delete(String key) {
        objects.remove(key);
        log.info("[local] 이미지 삭제: key={}", key);
    }
}
