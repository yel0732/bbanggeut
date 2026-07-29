package com.A501.backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.A501.backend.domain.auth.repository.ParentRepository;

@ExtendWith(MockitoExtension.class)
class AuthPurgeServiceTest {

    private static final LocalDateTime PURGE_BEFORE = LocalDateTime.of(2026, 7, 29, 12, 0);

    @Mock
    private ParentRepository parentRepository;

    @InjectMocks
    private AuthPurgeService authPurgeService;

    @Test
    void findWithdrawnParentIdsForPurge_passesLimitAsPageSize() {
        when(parentRepository.findIdsToPurge(eq(PURGE_BEFORE), any(Pageable.class)))
                .thenReturn(List.of(1L, 2L));

        assertThat(authPurgeService.findWithdrawnParentIdsForPurge(PURGE_BEFORE, 50))
                .containsExactly(1L, 2L);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(parentRepository).findIdsToPurge(eq(PURGE_BEFORE), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getPageNumber()).isZero();
    }

    // batch-size가 0이나 음수로 잘못 설정되면 PageRequest.of가 예외를 던져 스케줄러 전체가
    // 죽는다. 설정 실수로 파기 작업이 중단되지 않도록 조회 자체를 건너뛴다.
    @Test
    void findWithdrawnParentIdsForPurge_returnsEmptyWithoutQueryingWhenLimitIsNotPositive() {
        assertThat(authPurgeService.findWithdrawnParentIdsForPurge(PURGE_BEFORE, 0)).isEmpty();
        assertThat(authPurgeService.findWithdrawnParentIdsForPurge(PURGE_BEFORE, -1)).isEmpty();

        verify(parentRepository, never()).findIdsToPurge(any(), any());
    }

    @Test
    void findWithdrawnParentIdsForPurge_returnsEmptyWhenPurgeBeforeIsNull() {
        assertThat(authPurgeService.findWithdrawnParentIdsForPurge(null, 50)).isEmpty();

        verify(parentRepository, never()).findIdsToPurge(any(), any());
    }
}
