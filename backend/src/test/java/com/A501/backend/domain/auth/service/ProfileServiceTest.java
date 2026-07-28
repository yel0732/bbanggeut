package com.A501.backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.common.storage.ImageErrorCode;
import com.A501.backend.common.storage.ProfileImageService;
import com.A501.backend.domain.auth.entity.Parent;
import com.A501.backend.domain.auth.exception.AuthErrorCode;
import com.A501.backend.domain.auth.repository.ParentRepository;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    private static final String PRESET_KEY = "profile-presets/preset-2";
    private static final String UPLOAD_KEY = "profile-uploads/1/parent/abc";
    private static final String PRESET_URL = "https://cdn.example.com/" + PRESET_KEY;
    private static final String UPLOAD_URL = "https://upload.example.com/signed";

    @Mock
    private ParentRepository parentRepository;

    @Mock
    private ProfileImageService profileImageService;

    private ProfileService profileService;

    @BeforeEach
    void setUp() {
        // ParentFinder는 목이 아니라 실제 구현을 끼운다 — 소프트 삭제 필터가 실제로 도는지까지
        // 이 테스트가 계속 검증하도록 하기 위함이다.
        profileService = new ProfileService(new ParentFinder(parentRepository), profileImageService);
    }

    @Test
    void updateProfileImage_changesKeyAndReturnsResolvedUrl() {
        Parent parent = newParent();
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(profileImageService.resolveUrl(PRESET_KEY)).thenReturn(PRESET_URL);

        String url = profileService.updateProfileImage(1L, PRESET_KEY);

        assertThat(parent.getProfileImageKey()).isEqualTo(PRESET_KEY);
        assertThat(url).isEqualTo(PRESET_URL);
    }

    // 웹캠 사진에서 프리셋으로 바꾸면 더 이상 참조되지 않는 실물 사진을 즉시 파기해야 한다.
    @Test
    void updateProfileImage_deletesPreviousUploadedImage() {
        Parent parent = newParent();
        parent.changeProfileImage(UPLOAD_KEY);
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(profileImageService.resolveUrl(PRESET_KEY)).thenReturn(PRESET_URL);

        profileService.updateProfileImage(1L, PRESET_KEY);

        verify(profileImageService).deleteIfUpload(UPLOAD_KEY);
    }

    // 클라이언트가 프리셋이 아닌 키(예: 타인의 업로드 키)를 지정하면 거절되어야 한다.
    @Test
    void updateProfileImage_rejectsNonPresetKeyBeforeTouchingParent() {
        doThrow(new BusinessException(ImageErrorCode.INVALID_IMAGE_KEY))
                .when(profileImageService).assertValidPreset(UPLOAD_KEY);

        assertThatThrownBy(() -> profileService.updateProfileImage(1L, UPLOAD_KEY))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ImageErrorCode.INVALID_IMAGE_KEY));
        // 거절될 요청 때문에 이전 이미지를 지우면 안 된다.
        verify(profileImageService, never()).deleteIfUpload(any());
        verify(parentRepository, never()).findById(any());
    }

    @Test
    void updateProfileImage_throwsWhenParentNotFound() {
        when(parentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.updateProfileImage(99L, PRESET_KEY))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.PARENT_NOT_FOUND));
    }

    @Test
    void updateProfileImage_throwsWhenParentIsSoftDeleted() {
        Parent parent = newParent();
        ReflectionTestUtils.setField(parent, "deletedAt", LocalDateTime.now());
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> profileService.updateProfileImage(1L, PRESET_KEY))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.PARENT_NOT_FOUND));
    }

    @Test
    void uploadProfileImage_appliesUploadedKeyAndReturnsResolvedUrl() {
        Parent parent = newParent();
        parent.changeProfileImage(PRESET_KEY);
        MockMultipartFile file = new MockMultipartFile("file", "webcam.jpg", "image/jpeg", new byte[] {1, 2, 3});
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(profileImageService.parentImagePrefix(1L)).thenReturn("profile-uploads/1/parent/");
        when(profileImageService.uploadImage("profile-uploads/1/parent/", file)).thenReturn(UPLOAD_KEY);
        when(profileImageService.resolveUrl(UPLOAD_KEY)).thenReturn(UPLOAD_URL);

        String url = profileService.uploadProfileImage(1L, file);

        assertThat(parent.getProfileImageKey()).isEqualTo(UPLOAD_KEY);
        assertThat(url).isEqualTo(UPLOAD_URL);
    }

    // 이전 사진도 웹캠 사진이면 교체와 동시에 파기한다.
    @Test
    void uploadProfileImage_deletesPreviousUploadedImage() {
        Parent parent = newParent();
        parent.changeProfileImage(UPLOAD_KEY);
        MockMultipartFile file = new MockMultipartFile("file", "webcam.jpg", "image/jpeg", new byte[] {1, 2, 3});
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(profileImageService.parentImagePrefix(1L)).thenReturn("profile-uploads/1/parent/");
        when(profileImageService.uploadImage("profile-uploads/1/parent/", file)).thenReturn("profile-uploads/1/parent/new");
        when(profileImageService.resolveUrl("profile-uploads/1/parent/new")).thenReturn(UPLOAD_URL);

        profileService.uploadProfileImage(1L, file);

        verify(profileImageService).deleteIfUpload(UPLOAD_KEY);
    }

    // 업로드가 실패하면(형식·용량 위반 등) 기존 사진이 지워지거나 교체되면 안 된다.
    @Test
    void uploadProfileImage_keepsPreviousImageWhenUploadRejected() {
        Parent parent = newParent();
        parent.changeProfileImage(PRESET_KEY);
        MockMultipartFile file = new MockMultipartFile("file", "webcam.txt", "text/plain", new byte[] {1, 2, 3});
        when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(profileImageService.parentImagePrefix(1L)).thenReturn("profile-uploads/1/parent/");
        when(profileImageService.uploadImage("profile-uploads/1/parent/", file))
                .thenThrow(new BusinessException(ImageErrorCode.INVALID_IMAGE_TYPE));

        assertThatThrownBy(() -> profileService.uploadProfileImage(1L, file))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ImageErrorCode.INVALID_IMAGE_TYPE));
        assertThat(parent.getProfileImageKey()).isEqualTo(PRESET_KEY);
        verify(profileImageService, never()).deleteIfUpload(any());
    }

    private Parent newParent() {
        return Parent.createLocal("parent@example.com", "hashed-password", LocalDate.of(1990, 1, 1));
    }
}
