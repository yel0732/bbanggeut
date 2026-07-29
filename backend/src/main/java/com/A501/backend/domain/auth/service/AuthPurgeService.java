package com.A501.backend.domain.auth.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.auth.repository.ParentRepository;

import lombok.RequiredArgsConstructor;

/**
 * 탈퇴 계정의 물리 파기 중 auth 도메인이 담당하는 부분.
 *
 * <p>삭제 메서드(refresh token, parent)는 후속 커밋에서 추가한다. 지금은 파기 대상 조회만 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthPurgeService {

    private final ParentRepository parentRepository;

    /**
     * 파기 대상 계정 ID를 오래 전에 탈퇴한 순서로 최대 {@code limit}건 반환한다.
     *
     * <p>limit이 0 이하면 조회하지 않고 빈 목록을 준다. PageRequest가 size 0에서 예외를 던지므로
     * 설정값이 잘못 들어왔을 때 스케줄러 전체가 죽는 것을 막는다.
     */
    public List<Long> findWithdrawnParentIdsForPurge(LocalDateTime purgeBefore, int limit) {
        if (purgeBefore == null || limit < 1) {
            return List.of();
        }
        return parentRepository.findIdsToPurge(purgeBefore, PageRequest.of(0, limit));
    }
}
