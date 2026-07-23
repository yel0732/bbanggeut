package com.A501.backend.domain.game.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.A501.backend.domain.game.entity.GameSession;

public interface GameSessionRepository extends JpaRepository<GameSession, UUID> {
}
