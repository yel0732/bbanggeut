package com.A501.backend.domain.child.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.A501.backend.common.storage.ProfileImageService;
import com.A501.backend.domain.child.repository.ChildRepository;

@ExtendWith(MockitoExtension.class)
class ChildPurgeServiceImageTest {

    private static final String UPLOAD_A = "profile-uploads/1/10/a.jpg";
    private static final String UPLOAD_B = "profile-uploads/1/11/b.jpg";

    @Mock
    private ChildRepository childRepository;

    @Mock
    private ProfileImageService profileImageService;

    @InjectMocks
    private ChildPurgeService childPurgeService;

    @Test
    void deleteChildProfileImagesByParentId_purgesEveryChildKey() {
        when(childRepository.findAllProfileImageKeysByParentId(1L))
                .thenReturn(List.of(UPLOAD_A, UPLOAD_B));

        childPurgeService.deleteChildProfileImagesByParentId(1L);

        verify(profileImageService).deleteIfUpload(UPLOAD_A);
        verify(profileImageService).deleteIfUpload(UPLOAD_B);
    }

    // 한 자녀의 S3 삭제가 실패해도 나머지가 계속돼야 한다. 여기서 예외가 새어 나가면
    // 오케스트레이션이 중단되어 계정 파기 전체가 매 주기 실패한다.
    @Test
    void deleteChildProfileImagesByParentId_continuesAfterAFailure() {
        when(childRepository.findAllProfileImageKeysByParentId(1L))
                .thenReturn(List.of(UPLOAD_A, UPLOAD_B));
        doThrow(new RuntimeException("s3 down")).when(profileImageService).deleteIfUpload(UPLOAD_A);

        childPurgeService.deleteChildProfileImagesByParentId(1L);

        verify(profileImageService).deleteIfUpload(UPLOAD_B);
    }

    @Test
    void deleteChildProfileImagesByParentId_ignoresNullParentId() {
        childPurgeService.deleteChildProfileImagesByParentId(null);

        verify(childRepository, never()).findAllProfileImageKeysByParentId(any());
        verify(profileImageService, never()).deleteIfUpload(anyString());
    }
}
