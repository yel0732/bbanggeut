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
        Child alive = childRepository.save(Child.create(1L, "alive", "profile-presets/preset-1"));
        Child deleted = childRepository.save(Child.create(1L, "deleted", "profile-presets/preset-1"));
        deleted.softDelete(LocalDateTime.now());
        childRepository.save(deleted);

        assertThat(childRepository.findAllByParentIdAndDeletedAtIsNull(1L))
                .extracting(Child::getChildId)
                .containsExactly(alive.getChildId());
    }

    @Test
    void existsByParentIdAndNameAndDeletedAtIsNull_ignoresSoftDeletedNames() {
        Child deleted = childRepository.save(Child.create(1L, "chorong", "profile-presets/preset-1"));
        deleted.softDelete(LocalDateTime.now());
        childRepository.save(deleted);

        // 소프트 삭제된 이름은 재사용 가능해야 한다.
        assertThat(childRepository.existsByParentIdAndNameAndDeletedAtIsNull(1L, "chorong")).isFalse();

        childRepository.save(Child.create(1L, "chorong", "profile-presets/preset-2"));

        assertThat(childRepository.existsByParentIdAndNameAndDeletedAtIsNull(1L, "chorong")).isTrue();
    }

    @Test
    void existsByParentIdAndNameAndDeletedAtIsNull_isScopedToParent() {
        childRepository.save(Child.create(1L, "chorong", "profile-presets/preset-1"));

        assertThat(childRepository.existsByParentIdAndNameAndDeletedAtIsNull(2L, "chorong")).isFalse();
    }

    @Test
    void countByParentIdAndDeletedAtIsNull_excludesSoftDeletedChildren() {
        childRepository.save(Child.create(1L, "one", "profile-presets/preset-1"));
        Child deleted = childRepository.save(Child.create(1L, "two", "profile-presets/preset-1"));
        deleted.softDelete(LocalDateTime.now());
        childRepository.save(deleted);

        assertThat(childRepository.countByParentIdAndDeletedAtIsNull(1L)).isEqualTo(1);
    }

    @Test
    void findByChildIdAndDeletedAtIsNull_excludesSoftDeletedChild() {
        Child child = childRepository.save(Child.create(1L, "chorong", "profile-presets/preset-1"));
        child.softDelete(LocalDateTime.now());
        childRepository.save(child);

        assertThat(childRepository.findByChildIdAndDeletedAtIsNull(child.getChildId())).isEmpty();
    }
}
