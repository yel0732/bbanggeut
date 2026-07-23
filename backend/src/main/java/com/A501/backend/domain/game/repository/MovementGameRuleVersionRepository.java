package com.A501.backend.domain.game.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.A501.backend.domain.game.entity.MovementGameRuleVersion;

public interface MovementGameRuleVersionRepository
        extends JpaRepository<MovementGameRuleVersion, Long> {

    List<MovementGameRuleVersion> findAllByActiveTrueOrderByCreatedAtDesc();

    Optional<MovementGameRuleVersion> findByVersionCode(String versionCode);
}
