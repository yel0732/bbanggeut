package com.A501.backend.domain.child.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.A501.backend.domain.child.entity.Child;

public interface ChildRepository extends JpaRepository<Child, Long> {

    List<Child> findAllByParentIdAndDeletedAtIsNull(Long parentId);

    Optional<Child> findByChildIdAndDeletedAtIsNull(Long childId);

    boolean existsByParentIdAndNameAndDeletedAtIsNull(Long parentId, String name);

    long countByParentIdAndDeletedAtIsNull(Long parentId);
}
