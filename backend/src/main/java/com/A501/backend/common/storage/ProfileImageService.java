package com.A501.backend.common.storage;

import java.io.IOException;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.A501.backend.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;

// 프로필 사진의 검증 규칙과 키 정책을 담당한다. 실제 저장/조회/삭제는 ImageStorage가 맡고,
// 이 서비스는 "무엇을 허용할지"만 결정한다.
@Service
@RequiredArgsConstructor
public class ProfileImageService {

    private static final String JPEG = "image/jpeg";
    private static final String PNG = "image/png";

    // 확장자나 Content-Type 헤더는 클라이언트가 마음대로 보낼 수 있으므로 파일 앞부분의 시그니처를
    // 함께 확인한다. 선언한 타입과 실제 바이트가 다르면 거부한다.
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private final ImageStorage imageStorage;
    private final StorageProperties storageProperties;

    public String uploadImage(String keyPrefix, MultipartFile file) {
        // 호출부 실수로 공개 영역(프리셋)에 아이 사진이 올라가는 사고를 막는 내부 불변식이다.
        // 사용자 입력이 아니라 코드 버그이므로 ErrorCode로 다루지 않는다.
        if (!storageProperties.isUpload(keyPrefix)) {
            throw new IllegalStateException("업로드 키 접두사가 아닙니다: " + keyPrefix);
        }

        String contentType = normalizeContentType(file.getContentType());
        if (!JPEG.equals(contentType) && !PNG.equals(contentType)) {
            throw new BusinessException(ImageErrorCode.INVALID_IMAGE_TYPE);
        }
        if (file.getSize() > storageProperties.maxImageSize().toBytes()) {
            throw new BusinessException(ImageErrorCode.IMAGE_TOO_LARGE);
        }

        byte[] content = readBytes(file);
        // 선언된 크기(getSize)를 믿지 않고 실제로 읽은 바이트로 한 번 더 확인한다.
        if (content.length > storageProperties.maxImageSize().toBytes()) {
            throw new BusinessException(ImageErrorCode.IMAGE_TOO_LARGE);
        }
        if (!hasSignatureOf(content, contentType)) {
            throw new BusinessException(ImageErrorCode.INVALID_IMAGE_TYPE);
        }

        return imageStorage.upload(keyPrefix, content, contentType);
    }

    public String resolveUrl(String key) {
        return imageStorage.resolveUrl(key);
    }

    // 클라이언트가 키를 직접 보낼 수 있는 유일한 지점이다. 접두사만 보면 profile-presets/../ 같은
    // 경로 조작이나 존재하지 않는 키를 허용하게 되므로, 화이트리스트 정확 일치만 통과시킨다.
    public void assertValidPreset(String key) {
        if (key == null || !storageProperties.presetKeys().contains(key)) {
            throw new BusinessException(ImageErrorCode.INVALID_IMAGE_KEY);
        }
    }

    public boolean isUpload(String key) {
        return storageProperties.isUpload(key);
    }

    // 신규 계정·자녀의 기본 이미지이자, 업로드 사진을 파기한 뒤 되돌릴 키다.
    public String defaultPresetKey() {
        return storageProperties.defaultPresetKey();
    }

    // 업로드 키는 서버만 만든다 — 클라이언트가 경로를 지정할 수 있으면 타인 소유 경로에 쓰거나
    // 타인 사진을 자기 프로필로 붙일 수 있다. 소유자(parentId)를 경로에 넣어 정리 범위도 명확히 한다.
    public String parentImagePrefix(Long parentId) {
        return storageProperties.uploadPrefix() + parentId + "/parent/";
    }

    // 자녀는 childId로 한 단계 더 나눈다 — 자녀 삭제 시 그 자녀 것만 골라 지우기 쉽다.
    public String childImagePrefix(Long parentId, Long childId) {
        return storageProperties.uploadPrefix() + parentId + "/" + childId + "/";
    }

    // 프리셋은 모든 사용자가 공유하는 이미지라 지우면 안 된다. 사진 교체·자녀 삭제·회원 탈퇴처럼
    // "이전 이미지를 정리하는" 자리에서 업로드 객체만 골라 지우기 위한 메서드다.
    public void deleteIfUpload(String key) {
        if (storageProperties.isUpload(key)) {
            imageStorage.delete(key);
        }
    }

    // 자녀 삭제·회원 탈퇴용. 업로드(아이 실물 사진) 객체를 파기하고 **이후 저장해야 할 키**를 돌려준다.
    // 소프트 삭제라 DB 행은 남지만 사진은 즉시 파기하며, 지워진 객체를 가리키는 키가 남지 않도록
    // 기본 프리셋으로 되돌린다.
    //
    // 프리셋이었다면 아무것도 지우지 않고 그 키를 그대로 돌려준다 — 공유 자원이라 지우면 안 되고,
    // 사용자가 고른 프리셋을 굳이 기본값으로 덮을 이유도 없다.
    //
    // 부모(auth)와 자녀(child) 두 도메인이 같은 정책을 쓰므로 여기 한 곳에 둔다.
    public String purgeUploadedImage(String currentKey) {
        if (!storageProperties.isUpload(currentKey)) {
            return currentKey;
        }
        imageStorage.delete(currentKey);
        return storageProperties.defaultPresetKey();
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        // "image/jpeg; charset=..." 형태로 오는 경우가 있어 파라미터를 떼어낸다.
        int parameterIndex = contentType.indexOf(';');
        String mediaType = parameterIndex < 0 ? contentType : contentType.substring(0, parameterIndex);
        return mediaType.trim().toLowerCase(Locale.ROOT);
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ImageErrorCode.IMAGE_UPLOAD_FAILED);
        }
    }

    private boolean hasSignatureOf(byte[] content, String contentType) {
        byte[] signature = JPEG.equals(contentType) ? JPEG_SIGNATURE : PNG_SIGNATURE;
        if (content.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (content[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
