package com.A501.backend.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import com.A501.backend.common.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
class ProfileImageServiceTest {

    private static final String PRESET_KEY = "profile-presets/01.png";
    private static final String UPLOAD_PREFIX = "profile-uploads/1/parent/";

    // 실제 아동 사진을 픽스처로 쓰지 않는다(AGENTS.md 민감정보 규칙). 포맷 판별에 필요한 것은
    // 앞부분 시그니처뿐이므로 합성 바이트로 충분하다.
    private static final byte[] JPEG_HEADER = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_HEADER = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private static final StorageProperties PROPERTIES = new StorageProperties(
            "test-preset-bucket",
            "test-upload-bucket",
            "ap-northeast-2",
            "profile-presets/",
            "profile-uploads/",
            "https://cdn.example.com",
            PRESET_KEY,
            Set.of(PRESET_KEY, "profile-presets/02.png"),
            DataSize.ofKilobytes(100),
            Duration.ofMinutes(5));

    @Mock
    private ImageStorage imageStorage;

    private ProfileImageService profileImageService;

    @BeforeEach
    void setUp() {
        profileImageService = new ProfileImageService(imageStorage, PROPERTIES);
    }

    @Test
    void uploadImage_storesValidJpegAndReturnsKey() {
        when(imageStorage.upload(eq(UPLOAD_PREFIX), any(), eq("image/jpeg"))).thenReturn(UPLOAD_PREFIX + "abc");

        String key = profileImageService.uploadImage(UPLOAD_PREFIX, imageFile("image/jpeg", JPEG_HEADER, 1024));

        assertThat(key).isEqualTo(UPLOAD_PREFIX + "abc");
    }

    @Test
    void uploadImage_storesValidPng() {
        when(imageStorage.upload(eq(UPLOAD_PREFIX), any(), eq("image/png"))).thenReturn(UPLOAD_PREFIX + "abc");

        String key = profileImageService.uploadImage(UPLOAD_PREFIX, imageFile("image/png", PNG_HEADER, 1024));

        assertThat(key).isEqualTo(UPLOAD_PREFIX + "abc");
    }

    // "image/jpeg; charset=..." 형태로 오는 클라이언트가 있어 파라미터를 떼고 판정해야 한다.
    @Test
    void uploadImage_acceptsContentTypeWithParameters() {
        when(imageStorage.upload(eq(UPLOAD_PREFIX), any(), eq("image/jpeg"))).thenReturn(UPLOAD_PREFIX + "abc");

        String key = profileImageService.uploadImage(
                UPLOAD_PREFIX, imageFile("image/jpeg; charset=binary", JPEG_HEADER, 1024));

        assertThat(key).isEqualTo(UPLOAD_PREFIX + "abc");
    }

    @Test
    void uploadImage_rejectsUnsupportedContentType() {
        assertThatThrownBy(() -> profileImageService.uploadImage(
                UPLOAD_PREFIX, imageFile("image/gif", JPEG_HEADER, 1024)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ImageErrorCode.INVALID_IMAGE_TYPE));
        verify(imageStorage, never()).upload(any(), any(), any());
    }

    @Test
    void uploadImage_rejectsMissingContentType() {
        assertThatThrownBy(() -> profileImageService.uploadImage(
                UPLOAD_PREFIX, imageFile(null, JPEG_HEADER, 1024)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ImageErrorCode.INVALID_IMAGE_TYPE));
    }

    @Test
    void uploadImage_rejectsFileLargerThanLimit() {
        assertThatThrownBy(() -> profileImageService.uploadImage(
                UPLOAD_PREFIX, imageFile("image/jpeg", JPEG_HEADER, 200 * 1024)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ImageErrorCode.IMAGE_TOO_LARGE));
        verify(imageStorage, never()).upload(any(), any(), any());
    }

    // Content-Type 헤더는 클라이언트가 마음대로 보낼 수 있으므로, 확장자/헤더만 믿으면
    // 이미지가 아닌 파일이 올라간다.
    @Test
    void uploadImage_rejectsBytesThatAreNotAnImage() {
        byte[] notAnImage = "<?php echo 1; ?>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> profileImageService.uploadImage(
                UPLOAD_PREFIX, new MockMultipartFile("file", "a.jpg", "image/jpeg", notAnImage)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ImageErrorCode.INVALID_IMAGE_TYPE));
        verify(imageStorage, never()).upload(any(), any(), any());
    }

    // 선언한 타입과 실제 시그니처가 어긋나는 경우도 거부한다.
    @Test
    void uploadImage_rejectsContentTypeMismatchingSignature() {
        assertThatThrownBy(() -> profileImageService.uploadImage(
                UPLOAD_PREFIX, imageFile("image/png", JPEG_HEADER, 1024)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ImageErrorCode.INVALID_IMAGE_TYPE));
    }

    @Test
    void uploadImage_rejectsEmptyFile() {
        assertThatThrownBy(() -> profileImageService.uploadImage(
                UPLOAD_PREFIX, new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[0])))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ImageErrorCode.INVALID_IMAGE_TYPE));
    }

    // 공개 영역(프리셋)에 아이 사진이 올라가는 사고를 막는 내부 불변식이다.
    @Test
    void uploadImage_rejectsNonUploadPrefix() {
        assertThatThrownBy(() -> profileImageService.uploadImage(
                "profile-presets/", imageFile("image/jpeg", JPEG_HEADER, 1024)))
                .isInstanceOf(IllegalStateException.class);
        verify(imageStorage, never()).upload(any(), any(), any());
    }

    @Test
    void assertValidPreset_passesForWhitelistedKey() {
        profileImageService.assertValidPreset(PRESET_KEY);
    }

    @Test
    void assertValidPreset_rejectsUnknownPresetKey() {
        assertThatThrownBy(() -> profileImageService.assertValidPreset("profile-presets/99.png"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ImageErrorCode.INVALID_IMAGE_KEY));
    }

    // 접두사만 검사했다면 통과했을 경로 조작 입력이다.
    @Test
    void assertValidPreset_rejectsPathTraversalKey() {
        assertThatThrownBy(() -> profileImageService.assertValidPreset("profile-presets/../profile-uploads/2/parent/x"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ImageErrorCode.INVALID_IMAGE_KEY));
    }

    // 클라이언트가 타인의 업로드 키를 프리셋인 척 지정하는 것도 막는다.
    @Test
    void assertValidPreset_rejectsUploadKey() {
        assertThatThrownBy(() -> profileImageService.assertValidPreset("profile-uploads/2/parent/abc"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ImageErrorCode.INVALID_IMAGE_KEY));
    }

    @Test
    void assertValidPreset_rejectsNullKey() {
        assertThatThrownBy(() -> profileImageService.assertValidPreset(null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ImageErrorCode.INVALID_IMAGE_KEY));
    }

    @Test
    void isUpload_distinguishesUploadKeyFromPresetKey() {
        assertThat(profileImageService.isUpload("profile-uploads/1/parent/abc")).isTrue();
        assertThat(profileImageService.isUpload(PRESET_KEY)).isFalse();
        assertThat(profileImageService.isUpload(null)).isFalse();
    }

    @Test
    void deleteIfUpload_deletesUploadedObject() {
        profileImageService.deleteIfUpload("profile-uploads/1/parent/abc");

        verify(imageStorage).delete("profile-uploads/1/parent/abc");
    }

    // 프리셋은 모든 사용자가 공유하므로 지우면 안 된다.
    @Test
    void deleteIfUpload_doesNothingForPresetKey() {
        profileImageService.deleteIfUpload(PRESET_KEY);

        verify(imageStorage, never()).delete(any());
    }

    @Test
    void resolveUrl_delegatesToStorage() {
        when(imageStorage.resolveUrl(PRESET_KEY)).thenReturn("https://cdn.example.com/" + PRESET_KEY);

        assertThat(profileImageService.resolveUrl(PRESET_KEY)).isEqualTo("https://cdn.example.com/" + PRESET_KEY);
    }

    private MockMultipartFile imageFile(String contentType, byte[] header, int totalSize) {
        byte[] content = Arrays.copyOf(header, Math.max(totalSize, header.length));
        return new MockMultipartFile("file", "profile.img", contentType, content);
    }
}
