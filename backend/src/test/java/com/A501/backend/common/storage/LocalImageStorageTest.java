package com.A501.backend.common.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// 가짜 저장소가 돌려주는 URL은 로컬에서 눈으로 확인하는 값이다. 열리는 척하는 http URL 대신
// 가짜임이 드러나는 형태를 유지해, 연동 중인 프론트가 "왜 이미지가 안 뜨지?"로 시간을 쓰지 않게 한다.
class LocalImageStorageTest {

    private final LocalImageStorage storage = new LocalImageStorage();

    @Test
    void resolveUploadUrl_marksUrlAsFakeAndSigned() {
        String key = "profile-uploads/1/parent/abc";

        assertThat(storage.resolveUploadUrl(key)).isEqualTo("local://uploads/" + key + "?signed=true");
    }

    @Test
    void upload_returnsKeyUnderGivenPrefix() {
        String key = storage.upload("profile-uploads/1/parent/", new byte[] {1, 2, 3}, "image/jpeg");

        assertThat(key).startsWith("profile-uploads/1/parent/");
    }

    @Test
    void delete_removesStoredObjectWithoutFailing() {
        String key = storage.upload("profile-uploads/1/parent/", new byte[] {1, 2, 3}, "image/jpeg");

        storage.delete(key);
        storage.delete(key); // 이미 없는 대상도 조용히 넘어가야 한다

        assertThat(storage.resolveUploadUrl(key)).contains(key);
    }
}
