package com.A501.backend.domain.child.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.common.storage.ImageErrorCode;
import com.A501.backend.common.storage.ProfileImageService;
import com.A501.backend.domain.auth.exception.PinErrorCode;
import com.A501.backend.domain.auth.service.ParentFinder;
import com.A501.backend.domain.auth.service.PinService;
import com.A501.backend.domain.child.dto.response.ChildResponse;
import com.A501.backend.domain.child.entity.Child;
import com.A501.backend.domain.child.exception.ChildErrorCode;
import com.A501.backend.domain.child.repository.ChildRepository;
import com.A501.backend.domain.child.security.ChildProperties;
import com.A501.backend.domain.childdiary.service.ChildDiaryPurgeService;
import com.A501.backend.domain.conversation.service.ConversationPurgeService;
import com.A501.backend.domain.game.service.GamePurgeService;
import com.A501.backend.domain.report.service.ReportPurgeService;

@ExtendWith(MockitoExtension.class)
class ChildServiceTest {

    private static final ChildProperties CHILD_PROPERTIES = new ChildProperties(10);

    private static final String PRESET_KEY = "profile-presets/child-avatar";
    private static final String OTHER_PRESET_KEY = "profile-presets/child-avatar-tiger";
    private static final String UPLOAD_KEY = "profile-uploads/1/10/abc";
    private static final String PRESET_URL = "https://cdn.example.com/" + PRESET_KEY;
    private static final String OTHER_PRESET_URL = "https://cdn.example.com/" + OTHER_PRESET_KEY;
    private static final String UPLOAD_URL = "https://upload.example.com/signed";

    @Mock
    private ChildRepository childRepository;

    // 탈퇴 게이트용. 목이라 기본적으로 아무것도 하지 않으므로 "활성 계정"으로 동작한다.
    @Mock
    private ParentFinder parentFinder;

    @Mock
    private PinService pinService;

    @Mock
    private ProfileImageService profileImageService;

    @Mock
    private ChildDiaryPurgeService childDiaryPurgeService;

    @Mock
    private ReportPurgeService reportPurgeService;

    @Mock
    private ConversationPurgeService conversationPurgeService;

    @Mock
    private GamePurgeService gamePurgeService;

    private ChildService childService;

    @BeforeEach
    void setUp() {
        childService = new ChildService(
                childRepository,
                CHILD_PROPERTIES,
                parentFinder,
                pinService,
                profileImageService,
                childDiaryPurgeService,
                reportPurgeService,
                conversationPurgeService,
                gamePurgeService);
    }

    @Test
    void isNameAvailable_returnsTrueWhenNoActiveChildHasName() {
        when(childRepository.existsByParentIdAndNameAndDeletedAtIsNull(1L, "chorong")).thenReturn(false);

        assertThat(childService.isNameAvailable(1L, "chorong")).isTrue();
    }

    @Test
    void isNameAvailable_returnsFalseWhenActiveChildHasName() {
        when(childRepository.existsByParentIdAndNameAndDeletedAtIsNull(1L, "chorong")).thenReturn(true);

        assertThat(childService.isNameAvailable(1L, "chorong")).isFalse();
    }

    // 응답에는 저장된 키가 아니라 해석된 URL이 실려야 한다.
    @Test
    void listChildren_returnsResolvedImageUrlInsteadOfKey() {
        Child child = Child.create(1L, "chorong", PRESET_KEY);
        when(childRepository.findAllByParentIdAndDeletedAtIsNull(1L)).thenReturn(List.of(child));
        when(profileImageService.resolveUrl(PRESET_KEY)).thenReturn(PRESET_URL);

        List<ChildResponse> children = childService.listChildren(1L);

        assertThat(children).hasSize(1);
        assertThat(children.get(0).name()).isEqualTo("chorong");
        assertThat(children.get(0).profileImageUrl()).isEqualTo(PRESET_URL);
    }

    @Test
    void softDeleteChildrenOf_softDeletesAllActiveChildren() {
        Child first = Child.create(1L, "chorong", PRESET_KEY);
        Child second = Child.create(1L, "banjjak", OTHER_PRESET_KEY);
        when(childRepository.findAllByParentIdAndDeletedAtIsNull(1L)).thenReturn(List.of(first, second));

        childService.softDeleteChildrenOf(1L);

        assertThat(first.isDeleted()).isTrue();
        assertThat(second.isDeleted()).isTrue();
    }

    @Test
    void createChild_savesChildWhenPinSetAndUnderLimitAndNameAvailable() {
        when(pinService.hasPin(1L)).thenReturn(true);
        when(childRepository.countByParentIdAndDeletedAtIsNull(1L)).thenReturn(0L);
        when(childRepository.existsByParentIdAndNameAndDeletedAtIsNull(1L, "chorong")).thenReturn(false);
        when(childRepository.save(any(Child.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(profileImageService.resolveUrl(OTHER_PRESET_KEY)).thenReturn(OTHER_PRESET_URL);

        ChildResponse child = childService.createChild(1L, "chorong", OTHER_PRESET_KEY);

        assertThat(child.name()).isEqualTo("chorong");
        assertThat(child.profileImageUrl()).isEqualTo(OTHER_PRESET_URL);
        verify(profileImageService).assertValidPreset(OTHER_PRESET_KEY);
    }

    // imageKey는 선택값이다 — 생략하면 기본 프리셋으로 생성된다.
    @Test
    void createChild_usesDefaultPresetWhenImageKeyOmitted() {
        when(pinService.hasPin(1L)).thenReturn(true);
        when(childRepository.countByParentIdAndDeletedAtIsNull(1L)).thenReturn(0L);
        when(childRepository.existsByParentIdAndNameAndDeletedAtIsNull(1L, "chorong")).thenReturn(false);
        when(childRepository.save(any(Child.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(profileImageService.defaultChildPresetKey()).thenReturn(PRESET_KEY);
        when(profileImageService.resolveUrl(PRESET_KEY)).thenReturn(PRESET_URL);

        ChildResponse child = childService.createChild(1L, "chorong", null);

        assertThat(child.profileImageUrl()).isEqualTo(PRESET_URL);
        verify(profileImageService, never()).assertValidPreset(any());
    }

    // 클라이언트가 프리셋이 아닌 키(예: 타인의 업로드 키)를 보내면 생성 자체가 거절되어야 한다.
    @Test
    void createChild_rejectsNonPresetImageKey() {
        when(pinService.hasPin(1L)).thenReturn(true);
        when(childRepository.countByParentIdAndDeletedAtIsNull(1L)).thenReturn(0L);
        when(childRepository.existsByParentIdAndNameAndDeletedAtIsNull(1L, "chorong")).thenReturn(false);
        doThrow(new BusinessException(ImageErrorCode.INVALID_IMAGE_KEY))
                .when(profileImageService).assertValidPreset(UPLOAD_KEY);

        assertThatThrownBy(() -> childService.createChild(1L, "chorong", UPLOAD_KEY))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ImageErrorCode.INVALID_IMAGE_KEY));
        verify(childRepository, never()).save(any());
    }

    @Test
    void createChild_throwsWhenPinNotSet() {
        when(pinService.hasPin(1L)).thenReturn(false);

        assertThatThrownBy(() -> childService.createChild(1L, "chorong", PRESET_KEY))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PinErrorCode.PIN_NOT_SET));
        verify(childRepository, never()).save(any());
    }

    @Test
    void createChild_throwsWhenChildLimitReached() {
        when(pinService.hasPin(1L)).thenReturn(true);
        when(childRepository.countByParentIdAndDeletedAtIsNull(1L)).thenReturn(10L);

        assertThatThrownBy(() -> childService.createChild(1L, "chorong", PRESET_KEY))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ChildErrorCode.CHILD_LIMIT_EXCEEDED));
        verify(childRepository, never()).save(any());
    }

    @Test
    void createChild_throwsWhenNameAlreadyExists() {
        when(pinService.hasPin(1L)).thenReturn(true);
        when(childRepository.countByParentIdAndDeletedAtIsNull(1L)).thenReturn(0L);
        when(childRepository.existsByParentIdAndNameAndDeletedAtIsNull(1L, "chorong")).thenReturn(true);

        assertThatThrownBy(() -> childService.createChild(1L, "chorong", PRESET_KEY))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ChildErrorCode.CHILD_NAME_ALREADY_EXISTS));
        verify(childRepository, never()).save(any());
    }

    @Test
    void updateChild_renamesAndChangesImageWhenOwnedAndNameAvailable() {
        Child child = Child.create(1L, "chorong", PRESET_KEY);
        when(childRepository.findByChildIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(child));
        when(childRepository.existsByParentIdAndNameAndDeletedAtIsNull(1L, "banjjak")).thenReturn(false);
        when(profileImageService.resolveUrl(OTHER_PRESET_KEY)).thenReturn(OTHER_PRESET_URL);

        ChildResponse updated = childService.updateChild(1L, 10L, "banjjak", OTHER_PRESET_KEY);

        assertThat(updated.name()).isEqualTo("banjjak");
        assertThat(updated.profileImageUrl()).isEqualTo(OTHER_PRESET_URL);
        assertThat(child.getProfileImageKey()).isEqualTo(OTHER_PRESET_KEY);
    }

    // 웹캠 사진에서 프리셋으로 바꾸면 더 이상 참조되지 않는 아이 실물 사진을 즉시 파기해야 한다.
    @Test
    void updateChild_deletesPreviousUploadedImage() {
        Child child = Child.create(1L, "chorong", UPLOAD_KEY);
        when(childRepository.findByChildIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(child));
        when(profileImageService.resolveUrl(PRESET_KEY)).thenReturn(PRESET_URL);

        childService.updateChild(1L, 10L, "chorong", PRESET_KEY);

        verify(profileImageService).deleteIfUpload(UPLOAD_KEY);
    }

    @Test
    void updateChild_rejectsNonPresetImageKeyBeforeTouchingChild() {
        doThrow(new BusinessException(ImageErrorCode.INVALID_IMAGE_KEY))
                .when(profileImageService).assertValidPreset(UPLOAD_KEY);

        assertThatThrownBy(() -> childService.updateChild(1L, 10L, "banjjak", UPLOAD_KEY))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ImageErrorCode.INVALID_IMAGE_KEY));
        // 거절될 요청 때문에 이전 이미지를 지우면 안 된다.
        verify(profileImageService, never()).deleteIfUpload(any());
        verify(childRepository, never()).findByChildIdAndDeletedAtIsNull(any());
    }

    @Test
    void updateChild_allowsKeepingSameNameWithoutDuplicateCheck() {
        Child child = Child.create(1L, "chorong", PRESET_KEY);
        when(childRepository.findByChildIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(child));
        when(profileImageService.resolveUrl(OTHER_PRESET_KEY)).thenReturn(OTHER_PRESET_URL);

        ChildResponse updated = childService.updateChild(1L, 10L, "chorong", OTHER_PRESET_KEY);

        assertThat(updated.profileImageUrl()).isEqualTo(OTHER_PRESET_URL);
        verify(childRepository, never()).existsByParentIdAndNameAndDeletedAtIsNull(any(), any());
    }

    @Test
    void updateChild_throwsWhenChildNotFound() {
        when(childRepository.findByChildIdAndDeletedAtIsNull(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> childService.updateChild(1L, 10L, "banjjak", PRESET_KEY))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ChildErrorCode.CHILD_NOT_FOUND));
    }

    @Test
    void updateChild_throwsNotFoundWhenChildBelongsToAnotherParent() {
        Child child = Child.create(2L, "chorong", PRESET_KEY);
        when(childRepository.findByChildIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(child));

        assertThatThrownBy(() -> childService.updateChild(1L, 10L, "banjjak", PRESET_KEY))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ChildErrorCode.CHILD_NOT_FOUND));
    }

    @Test
    void updateChild_throwsWhenNewNameAlreadyUsedByAnotherChild() {
        Child child = Child.create(1L, "chorong", PRESET_KEY);
        when(childRepository.findByChildIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(child));
        when(childRepository.existsByParentIdAndNameAndDeletedAtIsNull(1L, "banjjak")).thenReturn(true);

        assertThatThrownBy(() -> childService.updateChild(1L, 10L, "banjjak", PRESET_KEY))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ChildErrorCode.CHILD_NAME_ALREADY_EXISTS));
        assertThat(child.getName()).isEqualTo("chorong");
    }

    @Test
    void uploadChildImage_appliesUploadedKeyAndReturnsResolvedUrl() {
        Child child = Child.create(1L, "chorong", PRESET_KEY);
        MockMultipartFile file = new MockMultipartFile("file", "webcam.jpg", "image/jpeg", new byte[] {1, 2, 3});
        when(childRepository.findByChildIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(child));
        when(profileImageService.childImagePrefix(1L, 10L)).thenReturn("profile-uploads/1/10/");
        when(profileImageService.uploadImage("profile-uploads/1/10/", file)).thenReturn(UPLOAD_KEY);
        when(profileImageService.resolveUrl(UPLOAD_KEY)).thenReturn(UPLOAD_URL);

        ChildResponse response = childService.uploadChildImage(1L, 10L, file);

        assertThat(child.getProfileImageKey()).isEqualTo(UPLOAD_KEY);
        assertThat(response.profileImageUrl()).isEqualTo(UPLOAD_URL);
    }

    // 남의 자녀 경로에 사진을 올리려는 시도는 업로드가 일어나기 전에 막혀야 한다.
    @Test
    void uploadChildImage_rejectsChildOfAnotherParentBeforeUploading() {
        Child child = Child.create(2L, "chorong", PRESET_KEY);
        MockMultipartFile file = new MockMultipartFile("file", "webcam.jpg", "image/jpeg", new byte[] {1, 2, 3});
        when(childRepository.findByChildIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(child));

        assertThatThrownBy(() -> childService.uploadChildImage(1L, 10L, file))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ChildErrorCode.CHILD_NOT_FOUND));
        verify(profileImageService, never()).uploadImage(any(), any());
    }

    @Test
    void uploadChildImage_deletesPreviousUploadedImage() {
        Child child = Child.create(1L, "chorong", UPLOAD_KEY);
        MockMultipartFile file = new MockMultipartFile("file", "webcam.jpg", "image/jpeg", new byte[] {1, 2, 3});
        when(childRepository.findByChildIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(child));
        when(profileImageService.childImagePrefix(1L, 10L)).thenReturn("profile-uploads/1/10/");
        when(profileImageService.uploadImage("profile-uploads/1/10/", file)).thenReturn("profile-uploads/1/10/new");
        when(profileImageService.resolveUrl("profile-uploads/1/10/new")).thenReturn(UPLOAD_URL);

        childService.uploadChildImage(1L, 10L, file);

        verify(profileImageService).deleteIfUpload(UPLOAD_KEY);
    }

    // 업로드가 거절되면 기존 사진이 지워지거나 교체되면 안 된다.
    @Test
    void uploadChildImage_keepsPreviousImageWhenUploadRejected() {
        Child child = Child.create(1L, "chorong", PRESET_KEY);
        MockMultipartFile file = new MockMultipartFile("file", "webcam.txt", "text/plain", new byte[] {1, 2, 3});
        when(childRepository.findByChildIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(child));
        when(profileImageService.childImagePrefix(1L, 10L)).thenReturn("profile-uploads/1/10/");
        when(profileImageService.uploadImage("profile-uploads/1/10/", file))
                .thenThrow(new BusinessException(ImageErrorCode.INVALID_IMAGE_TYPE));

        assertThatThrownBy(() -> childService.uploadChildImage(1L, 10L, file))
                .isInstanceOf(BusinessException.class);
        assertThat(child.getProfileImageKey()).isEqualTo(PRESET_KEY);
        verify(profileImageService, never()).deleteIfUpload(any());
    }

    @Test
    void deleteChild_hardDeletesChildAndRelatedDataWhenOwnedAndPinCorrect() {
        Child child = Child.create(1L, "chorong", PRESET_KEY);
        when(childRepository.findByChildIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(child));

        childService.deleteChild(1L, 10L, "1234");

        verify(pinService).verifyPin(1L, "1234");
        verify(childRepository).delete(child);
        verify(childDiaryPurgeService).purgeByChildIds(List.of(10L));
        verify(reportPurgeService).purgeByChildIds(List.of(10L));
        verify(conversationPurgeService).purgeByChildIds(List.of(10L));
        verify(gamePurgeService).purgeByChildIds(List.of(10L));
    }

    // 소프트 삭제라 DB 행은 남지만 아이 실물 사진은 즉시 파기되어야 한다.
    @Test
    void deleteChild_purgesUploadedImageAndResetsKey() {
        Child child = Child.create(1L, "chorong", UPLOAD_KEY);
        when(childRepository.findByChildIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(child));
        when(profileImageService.defaultChildPresetKey()).thenReturn(PRESET_KEY);
        when(profileImageService.purgeUploadedImage(UPLOAD_KEY, PRESET_KEY)).thenReturn(PRESET_KEY);

        childService.deleteChild(1L, 10L, "1234");

        assertThat(child.getProfileImageKey()).isEqualTo(PRESET_KEY);
        verify(childRepository).delete(child);
    }

    // PIN이 틀려 삭제가 거절되면 사진도 남아 있어야 한다.
    @Test
    void deleteChild_doesNotPurgeImageWhenPinRejected() {
        Child child = Child.create(1L, "chorong", UPLOAD_KEY);
        when(childRepository.findByChildIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(child));
        doThrow(new BusinessException(PinErrorCode.PIN_MISMATCH)).when(pinService).verifyPin(1L, "0000");

        assertThatThrownBy(() -> childService.deleteChild(1L, 10L, "0000"))
                .isInstanceOf(BusinessException.class);
        verify(profileImageService, never()).purgeUploadedImage(any(), any());
        assertThat(child.getProfileImageKey()).isEqualTo(UPLOAD_KEY);
    }

    // 탈퇴 경유 경로. 자녀가 여럿이면 각자의 사진이 모두 파기되어야 한다.
    @Test
    void softDeleteChildrenOf_purgesUploadedImageOfEveryChild() {
        Child uploaded = Child.create(1L, "chorong", UPLOAD_KEY);
        Child preset = Child.create(1L, "banjjak", OTHER_PRESET_KEY);
        when(childRepository.findAllByParentIdAndDeletedAtIsNull(1L)).thenReturn(List.of(uploaded, preset));
        when(profileImageService.defaultChildPresetKey()).thenReturn(PRESET_KEY);
        when(profileImageService.purgeUploadedImage(UPLOAD_KEY, PRESET_KEY)).thenReturn(PRESET_KEY);
        when(profileImageService.purgeUploadedImage(OTHER_PRESET_KEY, PRESET_KEY)).thenReturn(OTHER_PRESET_KEY);

        childService.softDeleteChildrenOf(1L);

        assertThat(uploaded.getProfileImageKey()).isEqualTo(PRESET_KEY);
        assertThat(preset.getProfileImageKey()).isEqualTo(OTHER_PRESET_KEY);
        verify(profileImageService).purgeUploadedImage(UPLOAD_KEY, PRESET_KEY);
        verify(profileImageService).purgeUploadedImage(OTHER_PRESET_KEY, PRESET_KEY);
    }

    @Test
    void deleteChild_throwsNotFoundBeforeCheckingPinWhenChildMissing() {
        when(childRepository.findByChildIdAndDeletedAtIsNull(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> childService.deleteChild(1L, 10L, "1234"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ChildErrorCode.CHILD_NOT_FOUND));
        // 존재하지 않는/소유하지 않은 자녀 요청은 PIN 실패 카운터를 소모하면 안 된다.
        verify(pinService, never()).verifyPin(any(), any());
    }

    @Test
    void deleteChild_throwsNotFoundWhenChildBelongsToAnotherParent() {
        Child child = Child.create(2L, "chorong", PRESET_KEY);
        when(childRepository.findByChildIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(child));

        assertThatThrownBy(() -> childService.deleteChild(1L, 10L, "1234"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ChildErrorCode.CHILD_NOT_FOUND));
        verify(pinService, never()).verifyPin(any(), any());
    }

    @Test
    void deleteChild_propagatesPinMismatchAndLeavesChildAlive() {
        Child child = Child.create(1L, "chorong", PRESET_KEY);
        when(childRepository.findByChildIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(child));
        doThrow(new BusinessException(PinErrorCode.PIN_MISMATCH)).when(pinService).verifyPin(1L, "0000");

        assertThatThrownBy(() -> childService.deleteChild(1L, 10L, "0000"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PinErrorCode.PIN_MISMATCH));
        assertThat(child.isDeleted()).isFalse();
    }
}
