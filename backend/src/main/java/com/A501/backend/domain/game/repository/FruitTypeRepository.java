package com.A501.backend.domain.game.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.A501.backend.domain.game.entity.FruitType;

public interface FruitTypeRepository extends JpaRepository<FruitType, Long> {
}
