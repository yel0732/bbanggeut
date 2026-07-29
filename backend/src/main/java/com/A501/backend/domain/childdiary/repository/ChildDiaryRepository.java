package com.A501.backend.domain.childdiary.repository;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.A501.backend.domain.childdiary.entity.ChildDiary;

public interface ChildDiaryRepository extends JpaRepository<ChildDiary, Long> {
    boolean existsByConversationId(String conversationId);
    Optional<ChildDiary> findByChildDiaryIdAndChildId(Long childDiaryId, Long childId);
    Page<ChildDiary> findAllByChildIdOrderByDiaryDateDescCreatedAtDesc(Long childId, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ChildDiary diary where diary.childId in :childIds")
    int deleteByChildIds(@Param("childIds") Collection<Long> childIds);
}
