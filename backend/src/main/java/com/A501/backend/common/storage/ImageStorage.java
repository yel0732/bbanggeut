package com.A501.backend.common.storage;

// 프로필 사진 저장소 추상화. auth(부모)·child(자녀) 두 도메인이 함께 쓰는 인프라라 common에 둔다.
//
// 키 하나로 프리셋과 업로드를 모두 표현하고, 접근 방식은 키 접두사로 구분한다
// (StorageProperties.isPreset/isUpload). 프리셋은 모두가 공유하는 공개 이미지, 업로드는
// 아이 실물 사진이 담기는 비공개 객체다.
public interface ImageStorage {

    // 바이트를 저장하고 저장된 키를 돌려준다. 키는 서버가 생성한다 — 클라이언트가 키를 지정할 수
    // 있으면 타인 경로에 덮어쓰기를 시도할 수 있기 때문이다.
    String upload(String keyPrefix, byte[] content, String contentType);

    // 키를 클라이언트가 실제로 접근할 수 있는 URL로 변환한다.
    // 프리셋 → 공개 URL, 업로드 → 단기 TTL presigned GET URL(소유자에게만 발급).
    String resolveUrl(String key);

    // 객체를 물리 삭제한다. 대상이 없으면 조용히 넘어간다.
    void delete(String key);
}
