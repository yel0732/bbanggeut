package com.A501.backend.domain.auth.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.A501.backend.domain.auth.entity.Parent;

public interface ParentRepository extends JpaRepository<Parent, Long> {

    Optional<Parent> findByEmail(String email);

    Optional<Parent> findByProviderId(String providerId);

    /**
     * 물리 파기 대상 계정 ID. 탈퇴했고(deleted_at 기록) 유예 기간이 지난 계정만 고른다.
     *
     * <p>엔티티가 아니라 ID만 조회하는 이유: 오케스트레이션이 parentId 하나로 도메인별 purge
     * service를 호출하고, 각 service가 자기 트랜잭션을 열기 때문이다. 여기서 엔티티를 들고
     * 있어봐야 그 영속성 컨텍스트는 이미 닫혀 있다.
     *
     * <p>한 번에 처리할 건수는 Pageable로 제한한다 — 탈퇴가 몰린 경우 한 주기에 전부 처리하려
     * 들면 스케줄 실행이 길어진다. 남은 계정은 다음 주기가 가져간다.
     */
    @Query("select p.parentId from Parent p "
            + "where p.deletedAt is not null and p.deletedAt <= :purgeBefore "
            + "order by p.deletedAt asc")
    List<Long> findIdsToPurge(@Param("purgeBefore") LocalDateTime purgeBefore, Pageable pageable);
}
