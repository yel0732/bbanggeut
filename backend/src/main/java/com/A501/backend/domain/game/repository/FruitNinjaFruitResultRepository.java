package com.A501.backend.domain.game.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.A501.backend.domain.game.entity.FruitNinjaFruitResult;

public interface FruitNinjaFruitResultRepository
        extends JpaRepository<FruitNinjaFruitResult, UUID> {

    List<FruitNinjaFruitResult> findAllByGameSessionIdOrderByFruitCode(UUID gameSessionId);
}
