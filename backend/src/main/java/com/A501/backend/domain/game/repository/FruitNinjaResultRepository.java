package com.A501.backend.domain.game.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.A501.backend.domain.game.entity.FruitNinjaResult;

public interface FruitNinjaResultRepository extends JpaRepository<FruitNinjaResult, UUID> {
}
