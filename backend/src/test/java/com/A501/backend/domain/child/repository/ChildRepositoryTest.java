package com.A501.backend.domain.child.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.A501.backend.config.JpaAuditingConfig;
import com.A501.backend.domain.child.entity.Child;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class ChildRepositoryTest {

    @Autowired
    private ChildRepository childRepository;

    @Test
    void findAllByParentIdAndDeletedAtIsNull_excludesSoftDeletedChildren() {
        Child alive = childRepository.save(Child.create(1L, "alive", "profile-presets/child-avatar"));
        Child deleted = childRepository.save(Child.create(1L, "deleted", "profile-presets/child-avatar"));
        deleted.softDelete(LocalDateTime.now());
        childRepository.save(deleted);

        assertThat(childRepository.findAllByParentIdAndDeletedAtIsNull(1L))
                .extracting(Child::getChildId)
                .containsExactly(alive.getChildId());
    }

    @Test
    void existsByParentIdAndNameAndDeletedAtIsNull_ignoresSoftDeletedNames() {
        Child deleted = childRepository.save(Child.create(1L, "chorong", "profile-presets/child-avatar"));
        deleted.softDelete(LocalDateTime.now());
        childRepository.save(deleted);

        // 소프트 삭제된 이름은 재사용 가능해야 한다.
        assertThat(childRepository.existsByParentIdAndNameAndDeletedAtIsNull(1L, "chorong")).isFalse();

        childRepository.save(Child.create(1L, "chorong", "profile-presets/child-avatar-tiger"));

        assertThat(childRepository.existsByParentIdAndNameAndDeletedAtIsNull(1L, "chorong")).isTrue();
    }

    @Test
    void existsByParentIdAndNameAndDeletedAtIsNull_isScopedToParent() {
        childRepository.save(Child.create(1L, "chorong", "profile-presets/child-avatar"));

        assertThat(childRepository.existsByParentIdAndNameAndDeletedAtIsNull(2L, "chorong")).isFalse();
    }

    @Test
    void countByParentIdAndDeletedAtIsNull_excludesSoftDeletedChildren() {
        childRepository.save(Child.create(1L, "one", "profile-presets/child-avatar"));
        Child deleted = childRepository.save(Child.create(1L, "two", "profile-presets/child-avatar"));
        deleted.softDelete(LocalDateTime.now());
        childRepository.save(deleted);

        assertThat(childRepository.countByParentIdAndDeletedAtIsNull(1L)).isEqualTo(1);
    }

    @Test
    void findByChildIdAndDeletedAtIsNull_excludesSoftDeletedChild() {
        Child child = childRepository.save(Child.create(1L, "chorong", "profile-presets/child-avatar"));
        child.softDelete(LocalDateTime.now());
        childRepository.save(child);

        assertThat(childRepository.findByChildIdAndDeletedAtIsNull(child.getChildId())).isEmpty();
    }

    // 계정 파기용 조회. 탈퇴 시 자녀도 소프트 삭제되므로 위 조회들(deleted_at IS NULL)로는
    // 파기 대상을 하나도 못 찾는다. 반대로 IS NOT NULL로 걸러도 안 된다 — 소프트 삭제가 부분
    // 실패한 자녀가 파기에서 빠져 고아 row로 남는다. 상태와 무관하게 전부 나와야 한다.
    @Test
    void findAllChildIdsByParentId_includesBothAliveAndSoftDeletedChildren() {
        Child alive = childRepository.save(Child.create(1L, "alive", "profile-presets/child-avatar"));
        Child deleted = childRepository.save(Child.create(1L, "deleted", "profile-presets/child-avatar"));
        deleted.softDelete(LocalDateTime.now());
        childRepository.save(deleted);

        assertThat(childRepository.findAllChildIdsByParentId(1L))
                .containsExactlyInAnyOrder(alive.getChildId(), deleted.getChildId());
    }

    @Test
    void findAllChildIdsByParentId_isScopedToParent() {
        childRepository.save(Child.create(1L, "mine", "profile-presets/child-avatar"));
        childRepository.save(Child.create(2L, "other", "profile-presets/child-avatar"));

        assertThat(childRepository.findAllChildIdsByParentId(1L)).hasSize(1);
    }

    @Test
    void findAllChildIdsByParentId_returnsEmptyWhenParentHasNoChildren() {
        assertThat(childRepository.findAllChildIdsByParentId(99L)).isEmpty();
    }
}
