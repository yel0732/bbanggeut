package com.A501.backend.common.storage;

// 업로드(웹캠 촬영) 사진 저장소 추상화. auth(부모)·child(자녀) 두 도메인이 함께 쓰는 인프라라
// common에 둔다.
//
// **프리셋은 이 저장소를 거치지 않는다.** 프리셋은 프론트엔드 번들 이미지이고 DB에는 식별자만
// 저장되므로, 여기서 다루는 것은 아이 실물 사진(비공개 S3 객체)뿐이다.
public interface ImageStorage {

    // 바이트를 저장하고 저장된 키를 돌려준다. 키는 서버가 생성한다 — 클라이언트가 키를 지정할 수
    // 있으면 타인 경로에 덮어쓰기를 시도할 수 있기 때문이다.
    String upload(String keyPrefix, byte[] content, String contentType);

    // 소유자에게만 발급되는 단기 TTL presigned GET URL을 만든다.
    String resolveUploadUrl(String key);

    // 객체를 물리 삭제한다. 대상이 없으면 조용히 넘어간다.
    void delete(String key);
}
