package com.A501.backend.domain.auth.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.A501.backend.domain.auth.entity.Parent;
import com.A501.backend.domain.auth.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByParentAndRevokedFalse(Parent parent);

    long deleteByExpiresAtBefore(LocalDateTime cutoff);

    /**
     * 계정 파기 시 해당 부모의 refresh token을 전부 물리 삭제한다. revoked 여부를 보지 않는다 —
     * 탈퇴 시 revoke된 토큰까지 남김없이 지워야 parent row를 삭제할 수 있다.
     *
     * <p>이 삭제를 건너뛰고 parent를 먼저 지우면 예외로 드러난다 — 영속성 컨텍스트에 토큰이
     * 올라와 있으면 Hibernate가 TransientPropertyValueException으로, 아니면 FK
     * (fk_refresh_token_parent)가 제약 위반으로 막는다. 도메인 간 참조 중 순서가 강제되는
     * 유일한 지점이며, 나머지는 순서를 틀려도 조용히 고아가 남는다
     * (backend/SCHEMA "외래 키 방침" 참고).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RefreshToken token where token.parent.parentId = :parentId")
    int deleteByParentId(@Param("parentId") Long parentId);
}
