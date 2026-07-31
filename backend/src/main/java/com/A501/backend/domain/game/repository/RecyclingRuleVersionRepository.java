package com.A501.backend.domain.game.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.A501.backend.domain.game.entity.RecyclingRuleVersion;

public interface RecyclingRuleVersionRepository
        extends JpaRepository<RecyclingRuleVersion, Long> {

    List<RecyclingRuleVersion> findAllByActiveTrueOrderByCreatedAtDesc();
}
