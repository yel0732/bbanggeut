package com.A501.backend.domain.child.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.A501.backend.domain.child.entity.Child;
import com.A501.backend.domain.child.repository.ChildRepository;

@SpringBootTest
@Transactional
class ChildPurgeServiceTest {

    private static final String PRESET_KEY = "profile-presets/child-avatar";

    @Autowired
    private ChildPurgeService childPurgeService;

    @Autowired
    private ChildRepository childRepository;

    // 탈퇴 시 자녀도 함께 소프트 삭제되므로, 삭제된 자녀가 빠지면 그 자녀의 리포트·대화 데이터가
    // 통째로 남는다. 이 테스트가 그 회귀를 막는다.
    @Test
    void findChildIdsIncludingDeleted_includesSoftDeletedChildren() {
        Child alive = childRepository.save(Child.create(1L, "alive", PRESET_KEY));
        Child deleted = childRepository.save(Child.create(1L, "deleted", PRESET_KEY));
        deleted.softDelete(LocalDateTime.now());
        childRepository.saveAndFlush(deleted);

        assertThat(childPurgeService.findChildIdsIncludingDeleted(1L))
                .containsExactlyInAnyOrder(alive.getChildId(), deleted.getChildId());
    }

    @Test
    void findChildIdsIncludingDeleted_returnsEmptyForNullParentId() {
        assertThat(childPurgeService.findChildIdsIncludingDeleted(null)).isEmpty();
    }

    @Test
    void hardDeleteByParentId_removesAliveAndSoftDeletedChildren() {
        childRepository.save(Child.create(2L, "alive", PRESET_KEY));
        Child deleted = childRepository.save(Child.create(2L, "deleted", PRESET_KEY));
        deleted.softDelete(LocalDateTime.now());
        childRepository.saveAndFlush(deleted);

        childPurgeService.hardDeleteByParentId(2L);

        assertThat(childRepository.findAllChildIdsByParentId(2L)).isEmpty();
    }

    @Test
    void hardDeleteByParentId_leavesOtherParentsChildrenAlone() {
        childRepository.save(Child.create(3L, "mine", PRESET_KEY));
        childRepository.saveAndFlush(Child.create(4L, "other", PRESET_KEY));

        childPurgeService.hardDeleteByParentId(3L);

        assertThat(childRepository.findAllChildIdsByParentId(3L)).isEmpty();
        assertThat(childRepository.findAllChildIdsByParentId(4L)).hasSize(1);
    }

    @Test
    void hardDeleteByParentId_isIdempotentAndIgnoresNull() {
        childRepository.saveAndFlush(Child.create(5L, "chorong", PRESET_KEY));

        childPurgeService.hardDeleteByParentId(5L);
        childPurgeService.hardDeleteByParentId(5L);
        childPurgeService.hardDeleteByParentId(null);

        assertThat(childRepository.findAllChildIdsByParentId(5L)).isEmpty();
    }
}
