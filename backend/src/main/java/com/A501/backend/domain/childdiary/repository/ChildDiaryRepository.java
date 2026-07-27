package com.A501.backend.domain.childdiary.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.A501.backend.domain.childdiary.entity.ChildDiary;

public interface ChildDiaryRepository extends JpaRepository<ChildDiary, Long> {
    boolean existsByConversationId(String conversationId);
    Optional<ChildDiary> findByChildDiaryIdAndChildId(Long childDiaryId, Long childId);
    Page<ChildDiary> findAllByChildIdOrderByDiaryDateDescCreatedAtDesc(Long childId, Pageable pageable);
}
