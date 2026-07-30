package com.A501.backend.domain.child.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.A501.backend.domain.child.entity.Child;

public interface ChildRepository extends JpaRepository<Child, Long> {

    List<Child> findAllByParentIdAndDeletedAtIsNull(Long parentId);

    Optional<Child> findByChildIdAndDeletedAtIsNull(Long childId);

    boolean existsByParentIdAndNameAndDeletedAtIsNull(Long parentId, String name);

    long countByParentIdAndDeletedAtIsNull(Long parentId);

    /**
     * 계정 파기 시 수집할 자녀 ID. **소프트 삭제 여부로 거르지 않는다.**
     *
     * <p>위 조회들은 전부 {@code deleted_at IS NULL} 필터가 붙어 있어 평소 기능에는 맞지만,
     * 탈퇴 시 자녀도 함께 소프트 삭제되므로 파기 대상을 하나도 찾지 못한다.
     *
     * <p>반대로 {@code IS NOT NULL}로 걸러도 안 된다. 탈퇴 중 자녀 소프트 삭제가 부분 실패하면
     * deleted_at이 비어 있는 자녀가 남는데, 그 자녀가 파기에서 누락된 채 parent row만 삭제되어
     * 고아가 된다. 부모를 지우기로 확정한 이상 자녀는 상태와 무관하게 전부 지워야 한다.
     *
     * <p>참고로 child.parent_id에는 FK가 없다(도메인 간 참조에 FK를 걸지 않는 이 스키마의
     * 방침 — backend/SCHEMA 참고). FK가 누락을 만드는 것은 아니지만, 있었다면 parent 삭제가
     * 실패하거나 cascade로 정리됐을 상황이 **아무 신호 없이** 지나간다는 뜻이다.
     * 삭제 순서와 누락 방지는 전적으로 애플리케이션 책임이다.
     */
    @Query("select c.childId from Child c where c.parentId = :parentId")
    List<Long> findAllChildIdsByParentId(@Param("parentId") Long parentId);

    /**
     * 계정 파기 시 정리할 자녀 프로필 이미지 키. 조회와 같은 이유로 소프트 삭제를 포함한다.
     */
    @Query("select c.profileImageKey from Child c where c.parentId = :parentId")
    List<String> findAllProfileImageKeysByParentId(@Param("parentId") Long parentId);

    /**
     * 계정 파기 시 해당 부모의 자녀를 전부 물리 삭제한다.
     * 조회와 같은 이유로 소프트 삭제 여부를 보지 않는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Child c where c.parentId = :parentId")
    int deleteByParentId(@Param("parentId") Long parentId);
}
