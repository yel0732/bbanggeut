package com.A501.backend.domain.game.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.A501.backend.domain.game.entity.RecyclingItem;

public interface RecyclingItemRepository extends JpaRepository<RecyclingItem, Long> {

    List<RecyclingItem> findAllByActiveTrueOrderByRecyclingItemIdAsc();

    boolean existsByItemCode(String itemCode);
}
