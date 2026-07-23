package com.A501.backend.domain.game.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.A501.backend.domain.game.entity.CardRound;

public interface CardRoundRepository extends JpaRepository<CardRound, UUID> {

    List<CardRound> findAllByGameSessionIdOrderByRoundNo(UUID gameSessionId);
}
