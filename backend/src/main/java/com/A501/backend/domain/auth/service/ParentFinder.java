package com.A501.backend.domain.auth.service;

import org.springframework.stereotype.Component;

import com.A501.backend.common.exception.BusinessException;
import com.A501.backend.domain.auth.entity.Parent;
import com.A501.backend.domain.auth.exception.AuthErrorCode;
import com.A501.backend.domain.auth.repository.ParentRepository;

import lombok.RequiredArgsConstructor;

// 인증된 요청이 자기 계정을 조회하는 단 하나의 통로. 소프트 삭제(탈퇴) 계정을 걸러내는 정책이
// 서비스마다 흩어지지 않도록 여기로 모은다 — JwtAuthenticationFilter는 토큰 서명만 검증하고
// 탈퇴 여부는 보지 않으므로, 탈퇴 계정이 만료 전 access token으로 인증 필요 API를 계속 쓰는 것을
// 막는 지점이 서비스 계층뿐이다.
//
// 트랜잭션 애노테이션을 일부러 붙이지 않는다: 호출한 서비스의 트랜잭션에 그대로 합류해야
// 반환된 Parent가 영속 상태로 남아 변경 감지(dirty checking)가 동작한다.
//
// 로그인·구글 연동처럼 계정 존재 여부를 숨겨야 하는 경로는 이 메서드를 쓰지 않는다. 그쪽은
// 탈퇴·비밀번호 불일치·미가입을 모두 INVALID_CREDENTIALS 하나로 응답해야 계정 열거를 막을 수
// 있는데, 여기서는 PARENT_NOT_FOUND를 던져 존재 여부가 드러나기 때문이다.
@Component
@RequiredArgsConstructor
public class ParentFinder {

    private final ParentRepository parentRepository;

    public Parent getActive(Long parentId) {
        return parentRepository.findById(parentId)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.PARENT_NOT_FOUND));
    }

    /**
     * 계정이 살아 있는지만 확인하고 엔티티는 돌려주지 않는다.
     *
     * <p>타 도메인(child)이 탈퇴 게이트를 적용하면서도 Parent 엔티티를 넘겨받지 않도록 하기
     * 위한 통로다. 엔티티가 도메인 밖으로 나가면 그쪽에서 상태를 바꿀 수 있게 되고,
     * 도메인 간 결합을 만들지 않는다는 이 프로젝트의 방침과 어긋난다.
     */
    public void assertActive(Long parentId) {
        getActive(parentId);
    }
}
